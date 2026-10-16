package lk.scot.ums.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import java.util.List;
import lk.scot.ums.auth.InvalidFirebaseSessionException;
import lk.scot.ums.auth.VerifiedSessionService;
import org.junit.jupiter.api.Test;

class StaffDirectoryServiceTest {
    private final Firestore firestore = mock(Firestore.class);
    private final VerifiedSessionService sessions = mock(VerifiedSessionService.class);
    private final StaffDirectoryService service = new StaffDirectoryService(firestore, sessions);

    @Test
    void requiresAnAuthenticatedSessionBeforeReadingStaffRecords() {
        when(sessions.verify("invalid-token")).thenThrow(new InvalidFirebaseSessionException());

        assertThrows(InvalidFirebaseSessionException.class, () -> service.list("invalid-token"));
        verifyNoInteractions(firestore);
    }

    @Test
    void returnsOnlyActiveStaffAndResolvesDepartmentAndSupervisor() throws Exception {
        QueryDocumentSnapshot staffMember = employee(
                "staff-1", "Permanent", "Alex Smith", "Alex", "alex@scot.lk",
                "department-1", "staff-2"
        );
        when(staffMember.get("designation")).thenReturn("Lecturer");
        when(staffMember.get("title")).thenReturn("Dr");
        when(staffMember.get("contactNumber")).thenReturn("+94771234567");
        when(staffMember.get("profilePhotoUrl")).thenReturn("https://example.com/alex.jpg");
        QueryDocumentSnapshot supervisor = employee(
                "staff-2", "Probationary", "Morgan Lee", "Morgan", "morgan@scot.lk",
                "department-1", ""
        );
        QueryDocumentSnapshot formerEmployee = employee(
                "staff-3", "Resigned", "Former Colleague", "", "former@scot.lk",
                "department-1", ""
        );

        CollectionReference employees = mock(CollectionReference.class);
        QuerySnapshot employeeSnapshot = mock(QuerySnapshot.class);
        when(firestore.collection("employees")).thenReturn(employees);
        when(employees.get()).thenReturn(ApiFutures.immediateFuture(employeeSnapshot));
        when(employeeSnapshot.getDocuments()).thenReturn(
                List.of(staffMember, supervisor, formerEmployee)
        );

        CollectionReference departments = mock(CollectionReference.class);
        QuerySnapshot departmentSnapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot department = mock(QueryDocumentSnapshot.class);
        when(firestore.collection("departments")).thenReturn(departments);
        when(departments.get()).thenReturn(ApiFutures.immediateFuture(departmentSnapshot));
        when(departmentSnapshot.getDocuments()).thenReturn(List.of(department));
        when(department.getId()).thenReturn("department-1");
        when(department.get("departmentName")).thenReturn("Computing");

        List<StaffDirectoryService.StaffMember> result = service.list("verified-token");

        assertEquals(2, result.size());
        StaffDirectoryService.StaffMember alex = result.get(0);
        assertEquals("Alex Smith", alex.fullName());
        assertEquals("alex@scot.lk", alex.email());
        assertEquals("+94771234567", alex.mobileNumber());
        assertEquals("Computing", alex.department());
        assertEquals("Morgan", alex.supervisor());
        assertEquals("https://example.com/alex.jpg", alex.photoUrl());
        verify(sessions).verify("verified-token");
    }

    private static QueryDocumentSnapshot employee(
            String id,
            String status,
            String fullName,
            String preferredName,
            String email,
            String department,
            String supervisor
    ) {
        QueryDocumentSnapshot employee = mock(QueryDocumentSnapshot.class);
        when(employee.getId()).thenReturn(id);
        when(employee.get("status")).thenReturn(status);
        when(employee.get("nameInFull")).thenReturn(fullName);
        when(employee.get("preferredName")).thenReturn(preferredName);
        when(employee.get("email")).thenReturn(email);
        when(employee.get("department")).thenReturn(department);
        when(employee.get("supervisor")).thenReturn(supervisor);
        return employee;
    }
}
