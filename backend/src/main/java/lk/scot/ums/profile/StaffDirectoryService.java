package lk.scot.ums.profile;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.DocumentReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import lk.scot.ums.auth.AmbiguousEmployeeException;
import lk.scot.ums.auth.DemoAuthService;
import lk.scot.ums.auth.EmployeeLookupException;
import lk.scot.ums.auth.VerifiedSessionService;
import lk.scot.ums.evaluation.EvaluationForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StaffDirectoryService {
    private static final Logger logger = LoggerFactory.getLogger(StaffDirectoryService.class);
    private static final Pattern SCOT_EMAIL = Pattern.compile("^[^@\\s]+@scot\\.lk$");

    private final Firestore firestore;
    private final VerifiedSessionService sessions;
    private volatile List<StaffMember> cachedStaffList = null;
    private volatile long staffListCacheExpiry = 0;
    private volatile List<DepartmentOption> cachedDepartments = null;
    private volatile long departmentsCacheExpiry = 0;
    private static final long DIRECTORY_CACHE_TTL_MS = 30_000;

    public StaffDirectoryService(Firestore firestore, VerifiedSessionService sessions) {
        this.firestore = firestore;
        this.sessions = sessions;
    }

    public List<StaffMember> list(String token) {
        sessions.verify(token);
        long now = System.currentTimeMillis();
        if (cachedStaffList != null && now < staffListCacheExpiry) {
            return cachedStaffList;
        }
        try {
            List<QueryDocumentSnapshot> employees = firestore.collection("employees")
                    .get().get().getDocuments();
            Map<String, String> departments = new HashMap<>();
            for (QueryDocumentSnapshot department : firestore.collection("departments")
                    .get().get().getDocuments()) {
                departments.put(department.getId(),
                        firstText(department, "departmentName", "name", "department"));
            }

            Map<String, String> namesById = new HashMap<>();
            for (QueryDocumentSnapshot employee : employees) {
                if (DemoAuthService.isCurrentEmployeeStatus(text(employee, "status"))) {
                    String name = firstText(employee, "preferredName", "nameInFull", "fullName");
                    namesById.put(employee.getId(), name);
                    String staffId = text(employee, "staffId");
                    if (!staffId.isBlank()) namesById.put(staffId, name);
                }
            }

            List<StaffMember> staff = new ArrayList<>();
            for (QueryDocumentSnapshot employee : employees) {
                if (!DemoAuthService.isCurrentEmployeeStatus(text(employee, "status"))) continue;

                staff.add(toStaffMember(employee, departments, namesById));
            }
            staff.sort(Comparator.comparing(
                    member -> firstNonBlank(member.preferredName(), member.fullName()),
                    String.CASE_INSENSITIVE_ORDER
            ));
            cachedStaffList = staff;
            staffListCacheExpiry = now + DIRECTORY_CACHE_TTL_MS;
            return staff;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Firestore staff directory lookup was interrupted");
            throw new EmployeeLookupException(exception);
        } catch (ExecutionException exception) {
            logger.error("Firestore staff directory lookup failed", exception.getCause());
            throw new EmployeeLookupException(exception.getCause());
        }
    }

    public List<DepartmentOption> departments(String token) {
        sessions.verify(token);
        long now = System.currentTimeMillis();
        if (cachedDepartments != null && now < departmentsCacheExpiry) {
            return cachedDepartments;
        }
        try {
            List<DepartmentOption> departments = new ArrayList<>();
            for (QueryDocumentSnapshot department : firestore.collection("departments")
                    .get().get().getDocuments()) {
                departments.add(new DepartmentOption(
                        department.getId(),
                        firstText(department, "departmentName", "name", "department")
                ));
            }
            departments.sort(Comparator.comparing(
                    DepartmentOption::name, String.CASE_INSENSITIVE_ORDER
            ));
            cachedDepartments = departments;
            departmentsCacheExpiry = now + DIRECTORY_CACHE_TTL_MS;
            return departments;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Firestore staff department lookup was interrupted");
            throw new EmployeeLookupException(exception);
        } catch (ExecutionException exception) {
            logger.error("Firestore staff department lookup failed", exception.getCause());
            throw new EmployeeLookupException(exception.getCause());
        }
    }

    public StaffMember update(String token, String employeeId, StaffUpdateRequest request) {
        if (!sessions.verify(token).evaluationAdmin()) {
            throw new EvaluationForbiddenException();
        }

        String fullName = clean(request.fullName());
        String email = clean(request.email()).toLowerCase(Locale.ROOT);
        String designation = clean(request.designation());
        String departmentId = clean(request.departmentId());
        String supervisorId = clean(request.supervisorId());
        String photoUrl = clean(request.photoUrl());
        if (fullName.isBlank() || designation.isBlank()) {
            throw new StaffDirectoryRequestException("Name and position are required.");
        }
        if (!SCOT_EMAIL.matcher(email).matches()) {
            throw new StaffDirectoryRequestException("Use a valid @scot.lk work email.");
        }
        if (!photoUrl.isBlank() && !photoUrl.matches("^https?://\\S+$")) {
            throw new StaffDirectoryRequestException("Profile photo must use a valid HTTP or HTTPS URL.");
        }

        try {
            DocumentReference employeeRef = firestore.collection("employees").document(employeeId);
            DocumentSnapshot employee = employeeRef.get().get();
            if (!employee.exists() || !DemoAuthService.isCurrentEmployeeStatus(text(employee, "status"))) {
                throw new StaffDirectoryRequestException("The selected active staff record no longer exists.");
            }

            List<QueryDocumentSnapshot> employees = firestore.collection("employees")
                    .get().get().getDocuments();
            Map<String, QueryDocumentSnapshot> activeEmployees = new HashMap<>();
            for (QueryDocumentSnapshot candidate : employees) {
                if (DemoAuthService.isCurrentEmployeeStatus(text(candidate, "status"))) {
                    activeEmployees.put(candidate.getId(), candidate);
                    String staffId = text(candidate, "staffId");
                    if (!staffId.isBlank()) activeEmployees.put(staffId, candidate);
                }
                if (!candidate.getId().equals(employeeId)
                        && email.equalsIgnoreCase(text(candidate, "email"))) {
                    throw new AmbiguousEmployeeException();
                }
            }
            if (!departmentId.isBlank()
                    && !firestore.collection("departments").document(departmentId).get().get().exists()) {
                throw new StaffDirectoryRequestException("Choose a valid department.");
            }
            if (!supervisorId.isBlank()) {
                QueryDocumentSnapshot supervisor = activeEmployees.get(supervisorId);
                if (supervisor == null) {
                    throw new StaffDirectoryRequestException("Choose an active employee as supervisor.");
                }
                if (supervisor.getId().equals(employeeId)) {
                    throw new StaffDirectoryRequestException("An employee cannot report to themself.");
                }
            }

            Map<String, Object> changes = new HashMap<>();
            changes.put("nameInFull", fullName);
            changes.put("preferredName", clean(request.preferredName()));
            changes.put("title", clean(request.title()));
            changes.put("designation", designation);
            changes.put("department", departmentId);
            changes.put("email", email);
            changes.put("contactNumber", clean(request.mobileNumber()));
            changes.put("supervisor", supervisorId);
            changes.put("photo", photoUrl);
            employeeRef.update(changes).get();
            DocumentSnapshot updated = employeeRef.get().get();
            String updatedDepartmentId = text(updated, "department");
            String departmentName = updatedDepartmentId.isBlank()
                    ? ""
                    : firstText(firestore.collection("departments")
                            .document(updatedDepartmentId).get().get(),
                            "departmentName", "name", "department");
            QueryDocumentSnapshot supervisor = supervisorId.isBlank()
                    ? null : activeEmployees.get(supervisorId);
            String supervisorName = supervisor == null
                    ? "" : firstText(supervisor, "preferredName", "nameInFull", "fullName");
            cachedStaffList = null;
            cachedDepartments = null;
            return toStaffMember(
                    updated,
                    Map.of(updatedDepartmentId, departmentName),
                    Map.of(supervisorId, supervisorName)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Firestore staff profile update was interrupted");
            throw new EmployeeLookupException(exception);
        } catch (ExecutionException exception) {
            logger.error("Firestore staff profile update failed", exception.getCause());
            throw new EmployeeLookupException(exception.getCause());
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static StaffMember toStaffMember(
            DocumentSnapshot employee,
            Map<String, String> departments,
            Map<String, String> namesById
    ) {
        String departmentId = text(employee, "department");
        String supervisorId = text(employee, "supervisor");
        String email = text(employee, "email").trim().toLowerCase(Locale.ROOT);
        return new StaffMember(
                employee.getId(),
                text(employee, "staffId"),
                text(employee, "nameInFull"),
                text(employee, "preferredName"),
                text(employee, "title"),
                text(employee, "designation"),
                departments.getOrDefault(departmentId, departmentId),
                departmentId,
                email.endsWith("@scot.lk") ? email : "",
                firstText(employee, "contactNumber", "mobileNumber"),
                supervisorId,
                namesById.getOrDefault(supervisorId, ""),
                firstText(employee, "profilePhotoUrl", "photoUrl", "photoURL", "avatarUrl", "photo")
        );
    }

    private static String firstText(DocumentSnapshot document, String... fields) {
        for (String field : fields) {
            String value = text(document, field);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String text(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value == null ? "" : value.toString().trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first.isBlank() ? second : first;
    }

    public record StaffMember(
            String id,
            String staffId,
            String fullName,
            String preferredName,
            String title,
            String designation,
            String department,
            String departmentId,
            String email,
            String mobileNumber,
            String supervisorId,
            String supervisor,
            String photoUrl
    ) {
    }

    public record DepartmentOption(String id, String name) {
    }

    public record StaffUpdateRequest(
            String fullName,
            String preferredName,
            String title,
            String designation,
            String departmentId,
            String email,
            String mobileNumber,
            String supervisorId,
            String photoUrl
    ) {
    }
}
