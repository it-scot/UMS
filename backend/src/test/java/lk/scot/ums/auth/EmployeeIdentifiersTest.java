package lk.scot.ums.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.firestore.DocumentSnapshot;
import org.junit.jupiter.api.Test;

class EmployeeIdentifiersTest {
    @Test
    void resolvesUppercaseStaffIdBeforeOtherAliases() {
        DocumentSnapshot employee = mock(DocumentSnapshot.class);
        when(employee.get("staffID")).thenReturn("  SCOT-1042  ");

        assertEquals("SCOT-1042", EmployeeIdentifiers.staffId(employee));
    }

    @Test
    void supportsEmployeeNumberAliasesAndFallsBackToDocumentId() {
        DocumentSnapshot employee = mock(DocumentSnapshot.class);
        when(employee.get("employeeNumber")).thenReturn("EMP-204");
        assertEquals("EMP-204", EmployeeIdentifiers.staffId(employee));

        DocumentSnapshot withoutIdentifier = mock(DocumentSnapshot.class);
        when(withoutIdentifier.getId()).thenReturn("document-id");
        assertEquals("document-id", EmployeeIdentifiers.staffId(withoutIdentifier));
    }
}