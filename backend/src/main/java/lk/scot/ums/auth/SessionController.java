package lk.scot.ums.auth;

import jakarta.servlet.http.HttpServletRequest;
import lk.scot.ums.profile.EmployeeProfile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class SessionController {
    private final VerifiedSessionService verifiedSessionService;

    public SessionController(VerifiedSessionService verifiedSessionService) {
        this.verifiedSessionService = verifiedSessionService;
    }

    @PostMapping("/session")
    public SessionResponse session(HttpServletRequest request) {
        VerifiedSession session = verifiedSessionService.verify(bearerToken(request));
        EmployeeProfile employee = session.employee();
        return new SessionResponse(
                employee.staffId(),
                employee.fullName(),
                employee.preferredName(),
                employee.title(),
                employee.designation(),
                employee.department(),
                employee.workEmail(),
                employee.dateJoined(),
                employee.status(),
                employee.role(),
                employee.supervisor(),
                session.evaluationAdmin(),
                session.itAdmin()
        );
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim()
                : "";
    }

    @ExceptionHandler(InvalidFirebaseSessionException.class)
    public ResponseEntity<ApiError> invalidSession() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_SESSION", "Sign in with a verified SCOT account."));
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ApiError> employeeNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("EMPLOYEE_NOT_FOUND", "No active staff record is linked to this account."));
    }

    @ExceptionHandler(AmbiguousEmployeeException.class)
    public ResponseEntity<ApiError> ambiguousEmployee() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("AMBIGUOUS_EMPLOYEE", "More than one employee record uses this email."));
    }

    @ExceptionHandler(EmployeeLookupException.class)
    public ResponseEntity<ApiError> employeeLookupFailed() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("EMPLOYEE_LOOKUP_FAILED", "Unable to load the verified staff session."));
    }

    public record SessionResponse(
            String staffId,
            String fullName,
            String preferredName,
            String title,
            String designation,
            String department,
            String workEmail,
            String dateJoined,
            String status,
            String role,
            String supervisor,
            boolean evaluationAdmin,
            boolean itAdmin
    ) {
    }

    public record ApiError(String code, String message) {
    }
}
