package lk.scot.ums.evaluation;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lk.scot.ums.auth.InvalidFirebaseSessionException;
import lk.scot.ums.auth.EmployeeNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {
    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/admin/dashboard")
    public Map<String, Object> dashboard(
            @RequestParam int year,
            HttpServletRequest request
    ) {
        return evaluationService.dashboard(token(request), year);
    }

    @GetMapping("/admin/templates")
    public Object templates(HttpServletRequest request) {
        return evaluationService.templates(token(request));
    }

    @PutMapping("/admin/templates/{templateId}")
    public Map<String, Object> saveTemplate(
            @PathVariable String templateId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        return evaluationService.saveTemplate(token(request), templateId, body);
    }

    @PostMapping("/admin/cycles/publish")
    public Map<String, Object> publishCycle(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        return evaluationService.publishCycle(token(request), body);
    }

    @GetMapping("/admin/results")
    public Map<String, Object> results(
            @RequestParam int year,
            HttpServletRequest request
    ) {
        return evaluationService.adminResults(token(request), year);
    }

    @PostMapping("/admin/rounds/{roundId}/results/publish")
    public Map<String, Object> publishResults(
            @PathVariable String roundId,
            HttpServletRequest request
    ) {
        return evaluationService.publishResults(token(request), roundId);
    }

    @PostMapping("/admin/assignments/{assignmentId}/approve-alternative")
    public Map<String, Object> approveAlternative(
            @PathVariable String assignmentId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        return evaluationService.approveAlternative(token(request), assignmentId, body);
    }

    @GetMapping("/me")
    public Map<String, Object> employeeView(HttpServletRequest request) {
        return evaluationService.employeeView(token(request));
    }

    @PostMapping("/assignments/{assignmentId}/responses")
    public Map<String, Object> respond(
            @PathVariable String assignmentId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        return evaluationService.respond(token(request), assignmentId, body);
    }

    @PostMapping("/assignments/{assignmentId}/confirmation")
    public Map<String, Object> confirmPeerAssignment(
            @PathVariable String assignmentId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        return evaluationService.confirmPeerAssignment(token(request), assignmentId, body);
    }

    @PostMapping("/results/{year}/acknowledgement")
    public Map<String, Object> acknowledgeResult(
            @PathVariable int year,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        return evaluationService.acknowledgeResult(token(request), year, body);
    }

    private static String token(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim()
                : "";
    }

    @ExceptionHandler(EvaluationForbiddenException.class)
    public ResponseEntity<ApiError> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "This action is not available to your account."));
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

    @ExceptionHandler(EvaluationRequestException.class)
    public ResponseEntity<ApiError> invalidRequest(EvaluationRequestException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_EVALUATION_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(EvaluationConflictException.class)
    public ResponseEntity<ApiError> conflict(EvaluationConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("EVALUATION_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(EvaluationDataException.class)
    public ResponseEntity<ApiError> dataFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("EVALUATION_DATA_FAILURE", "Unable to load or save evaluation data."));
    }

    public record ApiError(String code, String message) {
    }
}
