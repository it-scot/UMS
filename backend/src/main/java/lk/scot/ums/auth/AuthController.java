package lk.scot.ums.auth;

import jakarta.validation.Valid;
import java.net.InetAddress;
import java.util.Map;
import lk.scot.ums.profile.EmployeeProfile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final DemoAuthService demoAuthService;

    public AuthController(DemoAuthService demoAuthService) {
        this.demoAuthService = demoAuthService;
    }

    @PostMapping("/demo-login")
    public ResponseEntity<Map<String, Object>> demoLogin(
            @Valid @RequestBody DemoLoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        requireLocalRequest(httpRequest);
        EmployeeProfile employee = demoAuthService.login(request.email());
        String email = employee.workEmail().trim().toLowerCase(java.util.Locale.ROOT);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("staffId", employee.staffId());
        response.put("fullName", employee.fullName());
        response.put("preferredName", employee.preferredName());
        response.put("title", employee.title());
        response.put("designation", employee.designation());
        response.put("department", employee.department());
        response.put("workEmail", employee.workEmail());
        response.put("dateJoined", employee.dateJoined());
        response.put("status", employee.status());
        response.put("role", employee.role());
        response.put("supervisor", employee.supervisor());
        response.put("evaluationAdmin", java.util.Set.of("sheran@scot.lk", "it@scot.lk").contains(email));
        response.put("itAdmin", "it@scot.lk".equals(email));
        response.put("idToken", demoAuthService.createLocalSession(employee));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/demo-session")
    public SessionController.SessionResponse demoSession(
            jakarta.servlet.http.HttpServletRequest request
    ) {
        requireLocalRequest(request);
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim() : "";
        EmployeeProfile employee = demoAuthService.findLocalSession(token);
        if (employee == null) throw new InvalidFirebaseSessionException();
        String email = employee.workEmail().trim().toLowerCase(java.util.Locale.ROOT);
        return new SessionController.SessionResponse(
                employee.staffId(), employee.fullName(), employee.preferredName(),
                employee.title(), employee.designation(), employee.department(),
                employee.workEmail(), employee.dateJoined(), employee.status(),
                employee.role(), employee.supervisor(),
                java.util.Set.of("sheran@scot.lk", "it@scot.lk").contains(email),
                "it@scot.lk".equals(email)
        );
    }

    private void requireLocalRequest(jakarta.servlet.http.HttpServletRequest request) {
        try {
            if (!demoAuthService.isEnabled()
                    || !InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                throw new DemoLoginDisabledException();
            }
        } catch (java.net.UnknownHostException exception) {
            throw new DemoLoginDisabledException();
        }
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(
            DemoLoginDisabledException.class
    )
    public ResponseEntity<ApiError> demoLoginDisabled() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "DEMO_LOGIN_DISABLED",
                        "Demo sign-in is disabled. Enable it only for local development."
                ));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(InvalidFirebaseSessionException.class)
    public ResponseEntity<ApiError> invalidDemoSession() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_SESSION", "The local test session is no longer valid. Sign in again."));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(
            InvalidWorkEmailException.class
    )
    public ResponseEntity<ApiError> invalidEmail() {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_EMAIL", "Use your @scot.lk work email."));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(
            EmployeeNotFoundException.class
    )
    public ResponseEntity<ApiError> employeeNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(
                        "EMPLOYEE_NOT_FOUND",
                        "No active employee account was found for that work email."
                ));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(
            AmbiguousEmployeeException.class
    )
    public ResponseEntity<ApiError> ambiguousEmployee() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "AMBIGUOUS_EMPLOYEE",
                        "More than one employee record uses that email. Contact an administrator."
                ));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(
            EmployeeLookupException.class
    )
    public ResponseEntity<ApiError> employeeLookupFailed() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(
                        "EMPLOYEE_LOOKUP_FAILED",
                        "Unable to load the employee profile."
                ));
    }

    public record ApiError(String code, String message) {
    }
}
