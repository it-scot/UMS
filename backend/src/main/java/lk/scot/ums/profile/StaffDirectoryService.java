package lk.scot.ums.profile;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lk.scot.ums.auth.DemoAuthService;
import lk.scot.ums.auth.EmployeeLookupException;
import lk.scot.ums.auth.VerifiedSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StaffDirectoryService {
    private static final Logger logger = LoggerFactory.getLogger(StaffDirectoryService.class);

    private final Firestore firestore;
    private final VerifiedSessionService sessions;

    public StaffDirectoryService(Firestore firestore, VerifiedSessionService sessions) {
        this.firestore = firestore;
        this.sessions = sessions;
    }

    public List<StaffMember> list(String token) {
        sessions.verify(token);
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

                String departmentId = text(employee, "department");
                String supervisorId = text(employee, "supervisor");
                String email = text(employee, "email").trim().toLowerCase(Locale.ROOT);
                staff.add(new StaffMember(
                        text(employee, "nameInFull"),
                        text(employee, "preferredName"),
                        text(employee, "title"),
                        text(employee, "designation"),
                        departments.getOrDefault(departmentId, departmentId),
                        email.endsWith("@scot.lk") ? email : "",
                        firstText(employee, "contactNumber", "mobileNumber"),
                        namesById.getOrDefault(supervisorId, ""),
                        firstText(employee, "profilePhotoUrl", "photoUrl", "photoURL", "avatarUrl")
                ));
            }
            staff.sort(Comparator.comparing(
                    member -> firstNonBlank(member.preferredName(), member.fullName()),
                    String.CASE_INSENSITIVE_ORDER
            ));
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

    private static String firstText(QueryDocumentSnapshot document, String... fields) {
        for (String field : fields) {
            String value = text(document, field);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String text(QueryDocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value == null ? "" : value.toString().trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first.isBlank() ? second : first;
    }

    public record StaffMember(
            String fullName,
            String preferredName,
            String title,
            String designation,
            String department,
            String email,
            String mobileNumber,
            String supervisor,
            String photoUrl
    ) {
    }
}
