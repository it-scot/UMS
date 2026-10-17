package lk.scot.ums.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import java.util.List;
import java.util.Map;
import lk.scot.ums.auth.VerifiedSession;
import lk.scot.ums.auth.VerifiedSessionService;
import lk.scot.ums.profile.EmployeeProfile;
import org.junit.jupiter.api.Test;

class AttendanceServiceTest {
    private final Firestore firestore = mock(Firestore.class);
    private final VerifiedSessionService sessions = mock(VerifiedSessionService.class);
    private final AttendanceService service = new AttendanceService(firestore, sessions);

    @Test
    void queriesAttendanceUsingTheSignedInEmployeesStaffId() throws Exception {
        when(sessions.verify("staff-token"))
                .thenReturn(new VerifiedSession(employee(), false, false));

        CollectionReference attendance = mock(CollectionReference.class);
        Query employeeIdentifierQuery = mock(Query.class);
        Query pinQuery = mock(Query.class);
        QuerySnapshot emptyAttendanceSnapshot = mock(QuerySnapshot.class);
        QuerySnapshot attendanceSnapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot event = mock(QueryDocumentSnapshot.class);
        CollectionReference schedules = mock(CollectionReference.class);
        Query scheduleQuery = mock(Query.class);
        QuerySnapshot scheduleSnapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot schedule = mock(QueryDocumentSnapshot.class);
        when(firestore.collection("attendance")).thenReturn(attendance);
        when(attendance.whereEqualTo("employeeIdentifier", "staff-1")).thenReturn(employeeIdentifierQuery);
        when(employeeIdentifierQuery.get()).thenReturn(ApiFutures.immediateFuture(emptyAttendanceSnapshot));
        when(emptyAttendanceSnapshot.getDocuments()).thenReturn(List.of());
        when(attendance.whereEqualTo("pin", "staff-1")).thenReturn(pinQuery);
        when(pinQuery.get()).thenReturn(ApiFutures.immediateFuture(attendanceSnapshot));
        when(attendanceSnapshot.getDocuments()).thenReturn(List.of(event));
        when(event.getId()).thenReturn("event-1");
        when(event.get("eventTime")).thenReturn("2026-08-06T12:14:30");
        when(event.get("eventName")).thenReturn("Access granted");
        when(event.get("readerName")).thenReturn("Main entrance");
        when(event.get("areaName")).thenReturn("Campus");
        when(event.get("readerState")).thenReturn("Entry");
        when(event.get("verificationMode")).thenReturn("Card");
        when(firestore.collection("workSchedules")).thenReturn(schedules);
        when(schedules.whereEqualTo("employeeID", "staff-1")).thenReturn(scheduleQuery);
        when(scheduleQuery.get()).thenReturn(ApiFutures.immediateFuture(scheduleSnapshot));
        when(scheduleSnapshot.getDocuments()).thenReturn(List.of(schedule));
        when(schedule.get("day")).thenReturn("Monday");
        when(schedule.get("startTime")).thenReturn("08:30");
        when(schedule.get("endTime")).thenReturn("17:00");

        Map<String, Object> result = service.myAttendance("staff-token");

        assertEquals(true, result.get("configured"));
        assertEquals(1, ((List<?>) result.get("records")).size());
        assertEquals(1, ((List<?>) result.get("schedules")).size());
        verify(attendance).whereEqualTo("employeeIdentifier", "staff-1");
        verify(attendance).whereEqualTo("pin", "staff-1");
        verify(schedules).whereEqualTo("employeeID", "staff-1");
    }

    @Test
    void returnsUnconfiguredWhenTheSessionHasNoEmployeeIdentifier() {
        EmployeeProfile employee = new EmployeeProfile(
                "", "Alex Smith", "Alex", "", "Lecturer", "Computing",
                "alex@scot.lk", "", "Permanent", "Staff", "", "", ""
        );
        when(sessions.verify("staff-token"))
                .thenReturn(new VerifiedSession(employee, false, false));

        Map<String, Object> result = service.myAttendance("staff-token");

        assertEquals(false, result.get("configured"));
        assertEquals(List.of(), result.get("records"));
        verify(firestore, never()).collection("attendance");
    }

    private static EmployeeProfile employee() {
        return new EmployeeProfile(
                "staff-1", "Alex Smith", "Alex", "", "Lecturer", "Computing",
                "alex@scot.lk", "", "Permanent", "Staff", "", "", ""
        );
    }
}