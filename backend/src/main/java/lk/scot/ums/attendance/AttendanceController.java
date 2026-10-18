package lk.scot.ums.attendance;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lk.scot.ums.auth.EmployeeLookupException;
import lk.scot.ums.auth.InvalidFirebaseSessionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/me")
    public Map<String, Object> myAttendance(HttpServletRequest request) {
        return attendanceService.myAttendance(token(request));
    }

    @PostMapping("/punch")
    public Map<String, Object> recordPunch(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String type = body != null ? body.getOrDefault("type", "in") : "in";
        return attendanceService.recordPunch(token(request), type);
    }

    private static String token(HttpServletRequest request) {
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

    @ExceptionHandler(EmployeeLookupException.class)
    public ResponseEntity<ApiError> attendanceUnavailable() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("ATTENDANCE_UNAVAILABLE", "Unable to load attendance records."));
    }

    public record ApiError(String code, String message) {
    }
}