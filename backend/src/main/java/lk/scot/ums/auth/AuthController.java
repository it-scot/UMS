package lk.scot.ums.auth;

import jakarta.validation.Valid;
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
    public ResponseEntity<EmployeeProfile> demoLogin(
            @Valid @RequestBody DemoLoginRequest request
    ) {
        return ResponseEntity.ok(demoAuthService.login(request.email()));
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
