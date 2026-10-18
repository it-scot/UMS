package lk.scot.ums.profile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lk.scot.ums.auth.AmbiguousEmployeeException;
import lk.scot.ums.auth.EmployeeLookupException;
import lk.scot.ums.auth.EmployeeNotFoundException;
import lk.scot.ums.auth.InvalidFirebaseSessionException;
import lk.scot.ums.evaluation.EvaluationForbiddenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffDirectoryController {
    private final StaffDirectoryService staffDirectoryService;

    public StaffDirectoryController(StaffDirectoryService staffDirectoryService) {
        this.staffDirectoryService = staffDirectoryService;
    }

    @GetMapping
    public List<StaffDirectoryService.StaffMember> list(HttpServletRequest request) {
        return staffDirectoryService.list(bearerToken(request));
    }

    @GetMapping("/departments")
    public List<StaffDirectoryService.DepartmentOption> departments(HttpServletRequest request) {
        return staffDirectoryService.departments(bearerToken(request));
    }

    @PostMapping
    public ResponseEntity<StaffDirectoryService.StaffMember> create(
            @RequestBody StaffDirectoryService.StaffCreateRequest body,
            HttpServletRequest request
    ) {
        StaffDirectoryService.StaffMember member =
                staffDirectoryService.create(bearerToken(request), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PutMapping("/{employeeId}")
    public StaffDirectoryService.StaffMember update(
            @PathVariable String employeeId,
            @RequestBody StaffDirectoryService.StaffUpdateRequest body,
            HttpServletRequest request
    ) {
        return staffDirectoryService.update(bearerToken(request), employeeId, body);
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

    @ExceptionHandler(EvaluationForbiddenException.class)
    public ResponseEntity<ApiError> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "Only HR and IT administrators can edit staff profiles."));
    }

    @ExceptionHandler(StaffDirectoryRequestException.class)
    public ResponseEntity<ApiError> invalidProfile(StaffDirectoryRequestException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_STAFF_PROFILE", exception.getMessage()));
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ApiError> employeeNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("EMPLOYEE_NOT_FOUND", "No active staff record is linked to this account."));
    }

    @ExceptionHandler(AmbiguousEmployeeException.class)
    public ResponseEntity<ApiError> ambiguousEmployee() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("AMBIGUOUS_EMPLOYEE", "That work email is already assigned to another employee."));
    }

    @ExceptionHandler(EmployeeLookupException.class)
    public ResponseEntity<ApiError> directoryUnavailable() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("STAFF_DIRECTORY_UNAVAILABLE", "Unable to load the staff directory."));
    }

    public record ApiError(String code, String message) {
    }
}
