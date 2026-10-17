package lk.scot.ums.auth;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import lk.scot.ums.profile.EmployeeProfile;
import org.springframework.stereotype.Service;

@Service
public class VerifiedSessionService {
    private static final Set<String> HR_ADMIN_EMAILS =
            Set.of("sheran@scot.lk", "it@scot.lk");
    private static final String HR_EMAIL = "sheran@scot.lk";
    private static final String IT_EMAIL = "it@scot.lk";

    private final FirebaseAuth firebaseAuth;
    private final Firestore firestore;
    private final DemoAuthService demoAuthService;

    public VerifiedSessionService(
            FirebaseAuth firebaseAuth,
            Firestore firestore,
            DemoAuthService demoAuthService
    ) {
        this.firebaseAuth = firebaseAuth;
        this.firestore = firestore;
        this.demoAuthService = demoAuthService;
    }

    public VerifiedSession verify(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new InvalidFirebaseSessionException();
        }
        EmployeeProfile localEmployee = demoAuthService.findLocalSession(bearerToken);
        if (localEmployee != null) {
            String email = localEmployee.workEmail().trim().toLowerCase(Locale.ROOT);
            return new VerifiedSession(
                    localEmployee,
                    HR_ADMIN_EMAILS.contains(email),
                    IT_EMAIL.equals(email)
            );
        }

        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(bearerToken, true);
            String email = token.getEmail();
            if (email == null || !Boolean.TRUE.equals(token.getClaims().get("email_verified"))) {
                throw new InvalidFirebaseSessionException();
            }
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            boolean isHrAdmin = HR_EMAIL.equals(normalizedEmail);
            boolean isItAdmin = IT_EMAIL.equals(normalizedEmail);
            boolean isAdmin = HR_ADMIN_EMAILS.contains(normalizedEmail);

            EmployeeProfile employee = findEmployee(normalizedEmail);
            if (employee == null && !isAdmin) {
                throw new EmployeeNotFoundException();
            }
            if (employee == null) {
                employee = new EmployeeProfile(
                        "",
                        "",
                        normalizedEmail.equals(HR_EMAIL) ? "Sheran" : "IT Administrator",
                        "",
                        isHrAdmin ? "HR Manager" : "IT Administrator",
                        "",
                        normalizedEmail,
                        "",
                        "Active",
                        "Administrator",
                        "",
                        "",
                        ""
                );
            }
            return new VerifiedSession(employee, isAdmin, isItAdmin);
        } catch (FirebaseAuthException exception) {
            throw new InvalidFirebaseSessionException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmployeeLookupException(exception);
        } catch (ExecutionException exception) {
            throw new EmployeeLookupException(exception.getCause());
        }
    }

    private EmployeeProfile findEmployee(String email)
            throws InterruptedException, ExecutionException {
        ApiFuture<QuerySnapshot> query = firestore.collection("employees")
                .whereEqualTo("email", email)
                .get();
        List<QueryDocumentSnapshot> matches = query.get().getDocuments();
        if (matches.size() > 1) {
            throw new AmbiguousEmployeeException();
        }
        if (matches.isEmpty()) {
            return null;
        }

        DocumentSnapshot document = matches.get(0);
        String status = text(document, "status");
        if (!DemoAuthService.isCurrentEmployeeStatus(status)) {
            return null;
        }
        String staffId = EmployeeIdentifiers.staffId(document);
        String departmentId = text(document, "department");
        String department = departmentId;
        if (!departmentId.isBlank()) {
            DocumentSnapshot departmentDocument = firestore.collection("departments")
                    .document(departmentId)
                    .get()
                    .get();
            if (departmentDocument.exists() && !text(departmentDocument, "departmentName").isBlank()) {
                department = text(departmentDocument, "departmentName");
            }
        }
        String supervisorId = text(document, "supervisor");
        String supervisor = supervisorId;
        if (!supervisorId.isBlank()) {
            DocumentSnapshot supervisorDocument = firestore.collection("employees")
                    .document(supervisorId)
                    .get()
                    .get();
            if (supervisorDocument.exists()) {
                supervisor = text(supervisorDocument, "preferredName");
                if (supervisor.isBlank()) {
                    supervisor = text(supervisorDocument, "nameInFull");
                }
            }
        }
        return new EmployeeProfile(
                staffId,
                text(document, "nameInFull"),
                text(document, "preferredName"),
                text(document, "title"),
                text(document, "designation"),
                department,
                email,
                "",
                status,
                text(document, "userRole"),
                supervisor,
                firstText(document, "contactNumber", "mobileNumber"),
                firstText(document, "profilePhotoUrl", "photoUrl", "photoURL", "avatarUrl", "photo")
        );
    }

    private static String firstText(DocumentSnapshot document, String... fields) {
        for (String field : fields) {
            String value = text(document, field).trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String text(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value == null ? "" : value.toString();
    }
}
