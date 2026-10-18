package lk.scot.ums.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import java.util.List;
import java.util.Map;
import lk.scot.ums.auth.InvalidFirebaseSessionException;
import lk.scot.ums.auth.VerifiedSessionService;
import lk.scot.ums.auth.VerifiedSession;
import lk.scot.ums.evaluation.EvaluationForbiddenException;
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
    void refusesProfileChangesFromNonAdministratorsBeforeAccessingFirestore() {
        EmployeeProfile employee = new EmployeeProfile(
                "staff-1", "Alex Smith", "Alex", "", "Lecturer", "Computing",
                "alex@scot.lk", "", "Permanent", "Staff", "", "", ""
        );
        when(sessions.verify("staff-token"))
                .thenReturn(new VerifiedSession(employee, false, false));

        assertThrows(
                EvaluationForbiddenException.class,
                () -> service.update(
                        "staff-token",
                        "staff-1",
                        new StaffDirectoryService.StaffUpdateRequest(
                                "Alex Smith", "Alex", "", "Lecturer", "department-1",
                                "alex@scot.lk", "+94771234567", "", ""
                        )
                )
        );
        verifyNoInteractions(firestore);
    }

    @Test
    void refusesEmployeeCreationFromNonAdministrators() {
        EmployeeProfile employee = new EmployeeProfile(
                "staff-1", "Alex Smith", "Alex", "", "Lecturer", "Computing",
                "alex@scot.lk", "", "Permanent", "Staff", "", "", ""
        );
        when(sessions.verify("staff-token"))
                .thenReturn(new VerifiedSession(employee, false, false));

        assertThrows(
                EvaluationForbiddenException.class,
                () -> service.create(
                        "staff-token",
                        new StaffDirectoryService.StaffCreateRequest(
                                "158", "New Staff", "New", "Mr.", "Lecturer",
                                "department-1", "new@scot.lk", "+94771234567",
                                "", "", "2026-10-18", "Permanent", "Staff",
                                "Monday to Friday", "8:30 to 5:30"
                        )
                )
        );
        verifyNoInteractions(firestore);
    }

    @Test
    void hrCanUpdateTheSharedProfileRecord() throws Exception {
        EmployeeProfile admin = new EmployeeProfile(
                "hr-1", "HR Admin", "HR", "", "HR Manager", "",
                "sheran@scot.lk", "", "Permanent", "Administrator", "", "", ""
        );
        when(sessions.verify("hr-token")).thenReturn(new VerifiedSession(admin, true, false));

        QueryDocumentSnapshot current = employee(
                "staff-1", "Permanent", "Alex Smith", "Alex", "alex@scot.lk",
                "dept-1", "staff-2"
        );
        QueryDocumentSnapshot updated = employee(
                "staff-1", "Permanent", "Alexandra Smith", "Alex", "alexandra@scot.lk",
                "dept-1", "staff-2"
        );
        when(current.exists()).thenReturn(true);
        when(updated.get("title")).thenReturn("Dr");
        when(updated.get("designation")).thenReturn("Senior Lecturer");
        when(updated.get("contactNumber")).thenReturn("+94771112222");
        when(updated.get("photo")).thenReturn("https://example.com/alex.jpg");
        QueryDocumentSnapshot supervisor = employee(
                "staff-2", "Permanent", "Morgan Lee", "Morgan", "morgan@scot.lk",
                "dept-1", ""
        );
        CollectionReference employees = mock(CollectionReference.class);
        DocumentReference employeeRef = mock(DocumentReference.class);
        QuerySnapshot employeeSnapshot = mock(QuerySnapshot.class);
        when(firestore.collection("employees")).thenReturn(employees);
        when(employees.document("staff-1")).thenReturn(employeeRef);
        when(employeeRef.get()).thenReturn(
                ApiFutures.immediateFuture(current),
                ApiFutures.immediateFuture(updated)
        );
        when(employees.get()).thenReturn(ApiFutures.immediateFuture(employeeSnapshot));
        when(employeeSnapshot.getDocuments()).thenReturn(List.of(current, supervisor));
        when(employeeRef.update(org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(ApiFutures.immediateFuture(mock(WriteResult.class)));

        CollectionReference departments = mock(CollectionReference.class);
        DocumentReference departmentRef = mock(DocumentReference.class);
        QueryDocumentSnapshot department = mock(QueryDocumentSnapshot.class);
        when(firestore.collection("departments")).thenReturn(departments);
        when(departments.document("dept-1")).thenReturn(departmentRef);
        when(departmentRef.get()).thenReturn(ApiFutures.immediateFuture(department));
        when(department.exists()).thenReturn(true);
        when(department.get("departmentName")).thenReturn("Computing");
        when(department.get("name")).thenReturn("");
        when(department.get("department")).thenReturn("");

        StaffDirectoryService.StaffMember result = service.update(
                "hr-token",
                "staff-1",
                new StaffDirectoryService.StaffUpdateRequest(
                        "Alexandra Smith", "Alex", "Dr", "Senior Lecturer", "dept-1",
                        "alexandra@scot.lk", "+94771112222", "staff-2",
                        "https://example.com/alex.jpg"
                )
        );

        assertEquals("Alexandra Smith", result.fullName());
        assertEquals("alexandra@scot.lk", result.email());
        assertEquals("+94771112222", result.mobileNumber());
        assertEquals("Morgan", result.supervisor());
        assertEquals("https://example.com/alex.jpg", result.photoUrl());
        verify(employeeRef).update(org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(
                changes -> "alexandra@scot.lk".equals(changes.get("email"))
                        && "+94771112222".equals(changes.get("contactNumber"))
                        && "staff-2".equals(changes.get("supervisor"))
        ));
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
        when(sessions.verify("verified-token")).thenReturn(new VerifiedSession(
                new EmployeeProfile("staff-0", "Directory Viewer", "Viewer", "", "", "", "viewer@scot.lk", "", "Permanent", "Staff", "", "", ""),
                false,
                false
        ));
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

    @Test
    void sheranCanUpdateEmployeeProfileAndShiftAllocation() throws Exception {
        EmployeeProfile sheran = new EmployeeProfile(
                "sheran-id", "Sheran Gomez", "Sheran", "Mr.", "Senior HR Manager", "HR",
                "sheran@scot.lk", "+94771239999", "Permanent", "Administrator", "", "", ""
        );
        when(sessions.verify("sheran-token")).thenReturn(new VerifiedSession(sheran, true, false));

        QueryDocumentSnapshot current = employee(
                "staff-1", "Permanent", "Alex Smith", "Alex", "alex@scot.lk",
                "dept-1", "staff-2"
        );
        QueryDocumentSnapshot updated = employee(
                "staff-1", "Permanent", "Alex Smith Updated", "Alex", "alex@scot.lk",
                "dept-1", "staff-2"
        );
        when(current.exists()).thenReturn(true);
        when(current.get("staffId")).thenReturn("158");
        when(updated.get("staffId")).thenReturn("158");
        when(updated.get("title")).thenReturn("Dr.");
        when(updated.get("designation")).thenReturn("Associate Professor");
        when(updated.get("contactNumber")).thenReturn("+94771112222");
        when(updated.get("photo")).thenReturn("https://example.com/alex.jpg");
        when(updated.get("dateJoined")).thenReturn("2024-01-15");
        when(updated.get("status")).thenReturn("Permanent");
        when(updated.get("userRole")).thenReturn("Staff");
        when(updated.get("workingDays")).thenReturn("Thursday to Tuesday");
        when(updated.get("shiftTime")).thenReturn("8:00 to 5:00");

        CollectionReference employees = mock(CollectionReference.class);
        DocumentReference employeeRef = mock(DocumentReference.class);
        QuerySnapshot employeeSnapshot = mock(QuerySnapshot.class);
        when(firestore.collection("employees")).thenReturn(employees);
        when(employees.document("staff-1")).thenReturn(employeeRef);
        when(employeeRef.getId()).thenReturn("staff-1");
        when(employeeRef.get()).thenReturn(
                ApiFutures.immediateFuture(current),
                ApiFutures.immediateFuture(updated)
        );
        when(employees.get()).thenReturn(ApiFutures.immediateFuture(employeeSnapshot));
        when(employeeSnapshot.getDocuments()).thenReturn(List.of(current));
        when(employeeRef.update(org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(ApiFutures.immediateFuture(mock(WriteResult.class)));

        CollectionReference departments = mock(CollectionReference.class);
        DocumentReference departmentRef = mock(DocumentReference.class);
        QueryDocumentSnapshot department = mock(QueryDocumentSnapshot.class);
        when(firestore.collection("departments")).thenReturn(departments);
        when(departments.document("dept-1")).thenReturn(departmentRef);
        when(departmentRef.get()).thenReturn(ApiFutures.immediateFuture(department));
        when(department.exists()).thenReturn(true);
        when(department.get("departmentName")).thenReturn("Computing");

        StaffDirectoryService.StaffMember result = service.update(
                "sheran-token",
                "staff-1",
                new StaffDirectoryService.StaffUpdateRequest(
                        "158", "Alex Smith Updated", "Alex", "Dr.", "Associate Professor",
                        "dept-1", "alex@scot.lk", "+94771112222", "",
                        "https://example.com/alex.jpg", "2024-01-15", "Permanent", "Staff",
                        "Thursday to Tuesday", "8:00 to 5:00"
                )
        );

        assertEquals("Alex Smith Updated", result.fullName());
        assertEquals("158", result.staffId());
        assertEquals("2024-01-15", result.dateJoined());
        assertEquals("Thursday to Tuesday", result.workingDaysOption());
        assertEquals("8:00 to 5:00", result.shiftTimeOption());
        verify(employeeRef).update(org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(
                changes -> "Alex Smith Updated".equals(changes.get("nameInFull"))
                        && "158".equals(changes.get("staffId"))
                        && "Thursday to Tuesday".equals(changes.get("workingDays"))
                        && "8:00 to 5:00".equals(changes.get("shiftTime"))
        ));
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
