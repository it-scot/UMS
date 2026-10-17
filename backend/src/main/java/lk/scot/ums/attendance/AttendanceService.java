package lk.scot.ums.attendance;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lk.scot.ums.auth.EmployeeLookupException;
import lk.scot.ums.auth.VerifiedSession;
import lk.scot.ums.auth.VerifiedSessionService;
import org.springframework.stereotype.Service;

@Service
public class AttendanceService {
    private final Firestore firestore;
    private final VerifiedSessionService sessions;

    public AttendanceService(Firestore firestore, VerifiedSessionService sessions) {
        this.firestore = firestore;
        this.sessions = sessions;
    }

    public Map<String, Object> myAttendance(String token) {
        VerifiedSession session = sessions.verify(token);
        String staffId = session.employee().staffId().trim();
        if (staffId.isBlank()) return unconfigured();

        try {
            Map<String, Map<String, String>> recordsById = new LinkedHashMap<>();
            List<QueryDocumentSnapshot> attendanceDocuments = firestore.collection("attendance")
                    .whereEqualTo("employeeIdentifier", staffId)
                    .get().get().getDocuments();
            if (attendanceDocuments.isEmpty()) {
                attendanceDocuments = firestore.collection("attendance")
                        .whereEqualTo("pin", staffId)
                        .get().get().getDocuments();
            }
            for (QueryDocumentSnapshot document : attendanceDocuments) {
                Map<String, String> record = new HashMap<>();
                record.put("id", document.getId());
                record.put("eventTime", text(document, "eventTime"));
                record.put("eventName", text(document, "eventName"));
                record.put("readerName", text(document, "readerName"));
                record.put("areaName", text(document, "areaName"));
                record.put("readerState", text(document, "readerState"));
                record.put("verificationMode", text(document, "verificationMode"));
                recordsById.put(document.getId(), record);
            }
            List<Map<String, String>> records = new ArrayList<>(recordsById.values());
            records.sort(Comparator.comparing(
                    (Map<String, String> record) -> record.get("eventTime"),
                    Comparator.reverseOrder()
            ));

            List<Map<String, String>> schedules = new ArrayList<>();
            QuerySnapshot scheduleDocuments = firestore.collection("workSchedules")
                    .whereEqualTo("employeeID", staffId)
                    .get().get();
            for (QueryDocumentSnapshot document : scheduleDocuments.getDocuments()) {
                Map<String, String> schedule = new HashMap<>();
                schedule.put("day", text(document, "day"));
                schedule.put("startTime", text(document, "startTime"));
                schedule.put("endTime", text(document, "endTime"));
                schedules.add(schedule);
            }
            return Map.of("configured", true, "records", records, "schedules", schedules);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmployeeLookupException(exception);
        } catch (ExecutionException exception) {
            throw new EmployeeLookupException(exception.getCause());
        }
    }

    private static Map<String, Object> unconfigured() {
        return Map.of("configured", false, "records", List.of(), "schedules", List.of());
    }

    private static String text(QueryDocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value == null ? "" : value.toString().trim();
    }
}