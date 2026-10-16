package lk.scot.ums.auth;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import lk.scot.ums.profile.EmployeeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DemoAuthService {
    private static final Logger logger = LoggerFactory.getLogger(DemoAuthService.class);
    private static final Set<String> CURRENT_EMPLOYEE_STATUSES =
            Set.of("permanent", "probationary", "intern");
    private static final Set<String> LOCAL_ADMIN_EMAILS =
            Set.of("sheran@scot.lk", "it@scot.lk");

    private final Firestore firestore;
    private final boolean enabled;
    private final Map<String, EmployeeProfile> localSessions = new ConcurrentHashMap<>();

    public DemoAuthService(
            Firestore firestore,
            @Value("${ums.demo-auth.enabled:false}") boolean enabled
    ) {
        this.firestore = firestore;
        this.enabled = enabled;
    }

    public EmployeeProfile login(String email) {
        if (!enabled) {
            throw new DemoLoginDisabledException();
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("^[^@\\s]+@scot\\.lk$")) {
            throw new InvalidWorkEmailException();
        }

        try {
            ApiFuture<QuerySnapshot> result = firestore.collection("employees")
                    .whereEqualTo("email", normalizedEmail)
                    .get();
            List<QueryDocumentSnapshot> matches = result.get().getDocuments();
            if (matches.isEmpty()) {
                if (LOCAL_ADMIN_EMAILS.contains(normalizedEmail)) {
                    return new EmployeeProfile(
                            "",
                            "",
                            normalizedEmail.equals("sheran@scot.lk") ? "Sheran" : "IT Administrator",
                            "",
                            normalizedEmail.equals("sheran@scot.lk") ? "HR Manager" : "IT Administrator",
                            "",
                            normalizedEmail,
                            "",
                            "Active",
                            "Administrator",
                            ""
                    );
                }
                throw new EmployeeNotFoundException();
            }
            if (matches.size() > 1) {
                throw new AmbiguousEmployeeException();
            }

            DocumentSnapshot employee = matches.get(0);
            String status = text(employee, "status");
            if (!isCurrentEmployeeStatus(status)) {
                throw new EmployeeNotFoundException();
            }

            String staffId = text(employee, "staffId");
            if (staffId.isBlank()) {
                staffId = employee.getId();
            }

            return new EmployeeProfile(
                    staffId,
                    text(employee, "nameInFull"),
                    text(employee, "preferredName"),
                    text(employee, "title"),
                    text(employee, "designation"),
                    lookupName("departments", text(employee, "department"), "departmentName"),
                    text(employee, "email"),
                    date(employee.get("dateJoined")),
                    status,
                    text(employee, "userRole"),
                    supervisorName(text(employee, "supervisor"))
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Firestore employee lookup was interrupted");
            throw new EmployeeLookupException(exception);
        } catch (ExecutionException exception) {
            logger.error("Firestore employee lookup failed", exception.getCause());
            throw new EmployeeLookupException(exception.getCause());
        }
    }

    public String createLocalSession(EmployeeProfile employee) {
        if (!enabled) throw new DemoLoginDisabledException();
        String token = "local-demo-" + UUID.randomUUID();
        localSessions.put(token, employee);
        return token;
    }

    public EmployeeProfile findLocalSession(String token) {
        if (!enabled || token == null || !token.startsWith("local-demo-")) return null;
        return localSessions.get(token);
    }

    public boolean isEnabled() {
        return enabled;
    }

    static boolean isCurrentEmployeeStatus(String status) {
        return status != null
                && CURRENT_EMPLOYEE_STATUSES.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    private String lookupName(String collection, String documentId, String field)
            throws InterruptedException, ExecutionException {
        if (documentId.isBlank()) {
            return "";
        }
        DocumentSnapshot document = firestore.collection(collection)
                .document(documentId)
                .get()
                .get();
        return document.exists() && !text(document, field).isBlank()
                ? text(document, field)
                : documentId;
    }

    private String supervisorName(String supervisorId)
            throws InterruptedException, ExecutionException {
        if (supervisorId.isBlank()) {
            return "";
        }
        DocumentSnapshot supervisor = firestore.collection("employees")
                .document(supervisorId)
                .get()
                .get();
        if (!supervisor.exists()) {
            return supervisorId;
        }
        String preferredName = text(supervisor, "preferredName");
        return preferredName.isBlank()
                ? text(supervisor, "nameInFull")
                : preferredName;
    }

    private static String text(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value == null ? "" : value.toString();
    }

    private static String date(Object value) {
        if (value instanceof com.google.cloud.Timestamp timestamp) {
            return timestamp.toDate().toInstant().atZone(ZoneOffset.UTC)
                    .toLocalDate().toString();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneOffset.UTC)
                    .toLocalDate().toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
        }
        return value == null ? "" : value.toString();
    }
}
