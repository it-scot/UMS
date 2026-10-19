package lk.scot.ums.profile;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.DocumentReference;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import lk.scot.ums.auth.AmbiguousEmployeeException;
import lk.scot.ums.auth.DemoAuthService;
import lk.scot.ums.auth.EmployeeLookupException;
import lk.scot.ums.auth.VerifiedSession;
import lk.scot.ums.auth.VerifiedSessionService;
import lk.scot.ums.evaluation.EvaluationForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StaffDirectoryService {
    private static final Logger logger = LoggerFactory.getLogger(StaffDirectoryService.class);
    private static final Pattern SCOT_EMAIL = Pattern.compile("^[^@\\s]+@scot\\.lk$");
    private static final Set<String> HR_ADMIN_EMAILS =
            Set.of("sheran@scot.lk", "it@scot.lk", "hr@scot.lk", "disitha@scot.lk");

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

    public boolean isAuthorizedAdmin(String token) {
        try {
            VerifiedSession session = sessions.verify(token);
            if (session.evaluationAdmin()) return true;
            String email = session.employee().workEmail().trim().toLowerCase(Locale.ROOT);
            if (HR_ADMIN_EMAILS.contains(email)) return true;
            String role = session.employee().role();
            return role != null && (role.equalsIgnoreCase("Super Admin") || role.equalsIgnoreCase("Administrator") || role.equalsIgnoreCase("Admin"));
        } catch (Exception e) {
            return false;
        }
    }

    public StaffMember update(String token, String employeeId, StaffUpdateRequest request) {
        if (!isAuthorizedAdmin(token)) {
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
            if (!employee.exists()) {
                QuerySnapshot byStaffId = firestore.collection("employees")
                        .whereEqualTo("staffId", employeeId).get().get();
                if (!byStaffId.isEmpty()) {
                    employee = byStaffId.getDocuments().get(0);
                    employeeRef = employee.getReference();
                } else {
                    throw new StaffDirectoryRequestException("The selected staff record does not exist.");
                }
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
                if (!candidate.getId().equals(employeeRef.getId())
                        && DemoAuthService.isCurrentEmployeeStatus(text(candidate, "status"))
                        && email.equalsIgnoreCase(text(candidate, "email"))) {
                    throw new AmbiguousEmployeeException();
                }
            }
            if (!departmentId.isBlank()) {
                boolean deptFound = firestore.collection("departments").document(departmentId).get().get().exists();
                if (!deptFound) {
                    for (QueryDocumentSnapshot d : firestore.collection("departments").get().get().getDocuments()) {
                        String dName = firstText(d, "departmentName", "name", "department");
                        if (dName.equalsIgnoreCase(departmentId)) {
                            departmentId = d.getId();
                            deptFound = true;
                            break;
                        }
                    }
                }
                if (!deptFound) {
                    throw new StaffDirectoryRequestException("Choose a valid department.");
                }
            }
            if (!supervisorId.isBlank()) {
                QueryDocumentSnapshot supervisor = activeEmployees.get(supervisorId);
                if (supervisor == null) {
                    for (QueryDocumentSnapshot candidate : employees) {
                        if (candidate.getId().equals(supervisorId) || text(candidate, "staffId").equals(supervisorId)) {
                            supervisor = candidate;
                            break;
                        }
                    }
                }
                if (supervisor == null) {
                    throw new StaffDirectoryRequestException("Choose an active employee as supervisor.");
                }
                if (supervisor.getId().equals(employeeRef.getId())) {
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
            if (!clean(request.staffId()).isBlank()) {
                String newStaffId = clean(request.staffId());
                for (QueryDocumentSnapshot candidate : employees) {
                    if (!candidate.getId().equals(employeeRef.getId())
                            && DemoAuthService.isCurrentEmployeeStatus(text(candidate, "status"))
                            && newStaffId.equalsIgnoreCase(text(candidate, "staffId"))) {
                        throw new StaffDirectoryRequestException("Staff ID " + newStaffId + " is already in use.");
                    }
                }
                changes.put("staffId", newStaffId);
            }
            if (!clean(request.dateJoined()).isBlank()) {
                changes.put("dateJoined", clean(request.dateJoined()));
            }
            if (!clean(request.status()).isBlank()) {
                changes.put("status", clean(request.status()));
            }
            if (!clean(request.userRole()).isBlank()) {
                changes.put("userRole", clean(request.userRole()));
            }
            if (!clean(request.workingDaysOption()).isBlank() || !clean(request.shiftTimeOption()).isBlank()) {
                String targetStaffId = !clean(request.staffId()).isBlank()
                        ? clean(request.staffId())
                        : text(employee, "staffId");
                if (targetStaffId.isBlank()) targetStaffId = employeeRef.getId();
                allocateShift(targetStaffId, request.workingDaysOption(), request.shiftTimeOption());
                changes.put("workingDays", clean(request.workingDaysOption()));
                changes.put("shiftTime", clean(request.shiftTimeOption()));
            }
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

    public StaffMember create(String token, StaffCreateRequest request) {
        if (!isAuthorizedAdmin(token)) {
            throw new EvaluationForbiddenException();
        }

        String fullName = clean(request.fullName());
        String email = clean(request.email()).toLowerCase(Locale.ROOT);
        String designation = clean(request.designation());
        String departmentId = clean(request.departmentId());
        String supervisorId = clean(request.supervisorId());
        String photoUrl = clean(request.photoUrl());
        String preferredName = clean(request.preferredName());
        String title = clean(request.title());
        String mobileNumber = clean(request.mobileNumber());
        String dateJoined = clean(request.dateJoined());
        if (dateJoined.isBlank()) {
            dateJoined = java.time.LocalDate.now().toString();
        }
        String status = clean(request.status());
        if (status.isBlank()) {
            status = "Permanent";
        }
        String userRole = clean(request.userRole());
        if (userRole.isBlank()) {
            userRole = "Staff";
        }
        String workingDaysOption = clean(request.workingDaysOption());
        if (workingDaysOption.isBlank()) {
            workingDaysOption = "Monday to Friday";
        }
        String shiftTimeOption = clean(request.shiftTimeOption());
        if (shiftTimeOption.isBlank()) {
            shiftTimeOption = "8:30 to 5:30";
        }

        if (fullName.isBlank() || designation.isBlank()) {
            throw new StaffDirectoryRequestException("Name and position are required.");
        }
        if (!SCOT_EMAIL.matcher(email).matches()) {
            throw new StaffDirectoryRequestException("Use a valid @scot.lk work email.");
        }
        if (!photoUrl.isBlank() && !photoUrl.matches("^https?://\\S+$")) {
            throw new StaffDirectoryRequestException("Profile photo must use a valid HTTP or HTTPS URL.");
        }

        if (preferredName.isBlank()) {
            preferredName = fullName.split("\\s+")[0];
        }

        try {
            List<QueryDocumentSnapshot> employees = firestore.collection("employees")
                    .get().get().getDocuments();

            int maxNumericStaffId = 150;
            for (QueryDocumentSnapshot candidate : employees) {
                String existingEmail = text(candidate, "email");
                if (email.equalsIgnoreCase(existingEmail)
                        && DemoAuthService.isCurrentEmployeeStatus(text(candidate, "status"))) {
                    throw new AmbiguousEmployeeException();
                }
                String candidateStaffId = text(candidate, "staffId");
                if (candidateStaffId.matches("^\\d+$")) {
                    try {
                        int num = Integer.parseInt(candidateStaffId);
                        if (num > maxNumericStaffId) maxNumericStaffId = num;
                    } catch (NumberFormatException ignored) {}
                }
            }

            String staffId = clean(request.staffId());
            if (staffId.isBlank()) {
                staffId = String.valueOf(maxNumericStaffId + 1);
            } else {
                for (QueryDocumentSnapshot candidate : employees) {
                    if (staffId.equalsIgnoreCase(text(candidate, "staffId"))
                            && DemoAuthService.isCurrentEmployeeStatus(text(candidate, "status"))) {
                        throw new StaffDirectoryRequestException("Staff ID " + staffId + " is already in use.");
                    }
                }
            }

            if (!departmentId.isBlank()
                    && !firestore.collection("departments").document(departmentId).get().get().exists()) {
                throw new StaffDirectoryRequestException("Choose a valid department.");
            }
            if (!supervisorId.isBlank()) {
                boolean supervisorFound = false;
                for (QueryDocumentSnapshot candidate : employees) {
                    if ((candidate.getId().equals(supervisorId) || text(candidate, "staffId").equals(supervisorId))
                            && DemoAuthService.isCurrentEmployeeStatus(text(candidate, "status"))) {
                        supervisorFound = true;
                        break;
                    }
                }
                if (!supervisorFound) {
                    throw new StaffDirectoryRequestException("Choose an active employee as supervisor.");
                }
            }

            DocumentReference newEmployeeRef = firestore.collection("employees").document();
            Map<String, Object> data = new HashMap<>();
            data.put("staffId", staffId);
            data.put("nameInFull", fullName);
            data.put("preferredName", preferredName);
            data.put("title", title);
            data.put("designation", designation);
            data.put("department", departmentId);
            data.put("email", email);
            data.put("contactNumber", mobileNumber);
            data.put("supervisor", supervisorId);
            data.put("photo", photoUrl);
            data.put("dateJoined", dateJoined);
            data.put("status", status);
            data.put("userRole", userRole);
            data.put("workingDays", workingDaysOption);
            data.put("shiftTime", shiftTimeOption);

            newEmployeeRef.set(data).get();

            // Allocate shift schedules in workSchedules
            allocateShift(staffId, workingDaysOption, shiftTimeOption);

            cachedStaffList = null;
            cachedDepartments = null;

            String departmentName = departmentId.isBlank() ? ""
                    : firstText(firestore.collection("departments").document(departmentId).get().get(),
                    "departmentName", "name", "department");

            DocumentSnapshot createdDoc = newEmployeeRef.get().get();
            return toStaffMember(
                    createdDoc,
                    Map.of(departmentId, departmentName),
                    Map.of(supervisorId, "")
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Firestore staff profile creation was interrupted");
            throw new EmployeeLookupException(exception);
        } catch (ExecutionException exception) {
            logger.error("Firestore staff profile creation failed", exception.getCause());
            throw new EmployeeLookupException(exception.getCause());
        }
    }

    private void allocateShift(String staffId, String workingDaysOption, String shiftTimeOption) {
        if (staffId == null || staffId.isBlank() || firestore == null) return;

        List<String> days;
        String cleanDays = clean(workingDaysOption).toLowerCase(Locale.ROOT);
        if (cleanDays.contains("thursday") || cleanDays.contains("tuesday")) {
            days = List.of("Thursday", "Friday", "Saturday", "Sunday", "Monday", "Tuesday");
        } else {
            days = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");
        }

        String startTime;
        String endTime;
        String cleanTime = clean(shiftTimeOption);
        if (cleanTime.contains("8:00") || cleanTime.contains("08:00")) {
            startTime = "08:00";
            endTime = "17:00";
        } else if (cleanTime.contains("9:00") || cleanTime.contains("09:00")) {
            startTime = "09:00";
            endTime = "18:00";
        } else {
            startTime = "08:30";
            endTime = "17:30";
        }

        try {
            var schedulesCollection = firestore.collection("workSchedules");
            if (schedulesCollection == null) return;
            var query = schedulesCollection.whereEqualTo("employeeID", staffId);
            if (query != null) {
                var future = query.get();
                if (future != null) {
                    QuerySnapshot existing = future.get();
                    if (existing != null) {
                        for (QueryDocumentSnapshot doc : existing.getDocuments()) {
                            doc.getReference().delete().get();
                        }
                    }
                }
            }

            for (String day : days) {
                Map<String, Object> schedule = new HashMap<>();
                schedule.put("employeeID", staffId);
                schedule.put("day", day);
                schedule.put("startTime", startTime);
                schedule.put("endTime", endTime);
                schedulesCollection.add(schedule).get();
            }
        } catch (Exception e) {
            logger.warn("Could not save workSchedules for staffId {}: {}", staffId, e.getMessage());
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String date(Object value) {
        if (value == null) return "";
        if (value instanceof com.google.cloud.Timestamp ts) {
            return ts.toDate().toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString();
        }
        if (value instanceof java.util.Date d) {
            return d.toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString();
        }
        String str = value.toString().trim();
        if (str.length() >= 10 && str.charAt(4) == '-' && str.charAt(7) == '-') {
            return str.substring(0, 10);
        }
        return str;
    }

    private static StaffMember toStaffMember(
            DocumentSnapshot employee,
            Map<String, String> departments,
            Map<String, String> namesById
    ) {
        String departmentId = text(employee, "department");
        String supervisorId = text(employee, "supervisor");
        String email = text(employee, "email").trim().toLowerCase(Locale.ROOT);
        String workingDays = firstText(employee, "workingDays", "workingDaysOption");
        if (workingDays.isBlank()) workingDays = "Monday to Friday";
        String shiftTime = firstText(employee, "shiftTime", "shiftTimeOption");
        if (shiftTime.isBlank()) shiftTime = "8:30 to 5:30";
        String status = text(employee, "status");
        if (status.isBlank()) status = "Permanent";
        String userRole = text(employee, "userRole");
        if (userRole.isBlank()) userRole = "Staff";

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
                firstText(employee, "profilePhotoUrl", "photoUrl", "photoURL", "avatarUrl", "photo"),
                date(employee.get("dateJoined")),
                status,
                userRole,
                workingDays,
                shiftTime
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
            String photoUrl,
            String dateJoined,
            String status,
            String userRole,
            String workingDaysOption,
            String shiftTimeOption
    ) {
        public StaffMember(
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
            this(id, staffId, fullName, preferredName, title, designation, department, departmentId, email, mobileNumber, supervisorId, supervisor, photoUrl, "", "Permanent", "Staff", "Monday to Friday", "8:30 to 5:30");
        }
    }

    public record DepartmentOption(String id, String name) {
    }

    public record StaffUpdateRequest(
            String staffId,
            String fullName,
            String preferredName,
            String title,
            String designation,
            String departmentId,
            String email,
            String mobileNumber,
            String supervisorId,
            String photoUrl,
            String dateJoined,
            String status,
            String userRole,
            String workingDaysOption,
            String shiftTimeOption
    ) {
        public StaffUpdateRequest(
                String fullName,
                String preferredName,
                String title,
                String designation,
                String departmentId,
                String email,
                String mobileNumber,
                String supervisorId,
                String photoUrl,
                String workingDaysOption,
                String shiftTimeOption
        ) {
            this("", fullName, preferredName, title, designation, departmentId, email, mobileNumber, supervisorId, photoUrl, "", "", "", workingDaysOption, shiftTimeOption);
        }

        public StaffUpdateRequest(
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
            this("", fullName, preferredName, title, designation, departmentId, email, mobileNumber, supervisorId, photoUrl, "", "", "", "", "");
        }
    }

    public record StaffCreateRequest(
            String staffId,
            String fullName,
            String preferredName,
            String title,
            String designation,
            String departmentId,
            String email,
            String mobileNumber,
            String supervisorId,
            String photoUrl,
            String dateJoined,
            String status,
            String userRole,
            String workingDaysOption,
            String shiftTimeOption
    ) {
    }
}
