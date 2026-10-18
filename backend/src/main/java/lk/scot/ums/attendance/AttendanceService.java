package lk.scot.ums.attendance;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
                schedule.put("startTime", scheduleTime(document, "startTime"));
                schedule.put("endTime", scheduleTime(document, "endTime"));
                schedules.add(schedule);
            }
            if (schedules.isEmpty()) {
                schedules.addAll(defaultSchedules());
            }
            return Map.of("configured", true, "records", records, "schedules", schedules);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmployeeLookupException(exception);
        } catch (ExecutionException exception) {
            throw new EmployeeLookupException(exception.getCause());
        }
    }

    public Map<String, Object> recordPunch(String token, String type) {
        VerifiedSession session = sessions.verify(token);
        String staffId = session.employee().staffId().trim();
        if (staffId.isBlank()) {
            staffId = "DEV-" + Math.abs(session.employee().workEmail().hashCode() % 1000);
        }
        String employeeName = session.employee().preferredName().isBlank()
                ? session.employee().fullName() : session.employee().preferredName();

        String nowIso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                .withZone(ZoneId.of("Asia/Colombo"))
                .format(Instant.now());

        boolean isOut = "out".equalsIgnoreCase(type) || "leave".equalsIgnoreCase(type);
        Map<String, Object> event = new HashMap<>();
        event.put("employeeIdentifier", staffId);
        event.put("pin", staffId);
        event.put("employeeName", employeeName);
        event.put("eventTime", nowIso);
        event.put("eventName", isOut ? "Clock Out / Leave" : "Clock In / Arrival");
        event.put("readerState", isOut ? "Exit" : "Entry");
        event.put("readerName", "Web Portal Punch");
        event.put("areaName", "Main Campus");
        event.put("verificationMode", "Self Service");

        try {
            firestore.collection("attendance").add(event).get();
            return Map.of("success", true, "type", isOut ? "OUT" : "IN", "eventTime", nowIso);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmployeeLookupException(exception);
        } catch (ExecutionException exception) {
            throw new EmployeeLookupException(exception.getCause());
        }
    }

    private static List<Map<String, String>> defaultSchedules() {
        List<Map<String, String>> defaults = new ArrayList<>();
        for (String day : List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")) {
            Map<String, String> schedule = new HashMap<>();
            schedule.put("day", day);
            schedule.put("startTime", "08:30");
            schedule.put("endTime", "17:30");
            defaults.add(schedule);
        }
        return defaults;
    }

    private static Map<String, Object> unconfigured() {
        return Map.of("configured", false, "records", List.of(), "schedules", defaultSchedules());
    }

    private static String text(QueryDocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value == null ? "" : value.toString().trim();
    }

    private static String scheduleTime(QueryDocumentSnapshot document, String field) {
        Object value = document.get(field);
        if (value instanceof Number fraction && fraction.doubleValue() >= 0 && fraction.doubleValue() < 1) {
            int minutes = (int) Math.round(fraction.doubleValue() * 24 * 60) % (24 * 60);
            return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
        }
        if (value instanceof Timestamp timestamp) {
            return DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()));
        }
        return value == null ? "" : value.toString().trim();
    }
}