package lk.scot.ums.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;

class DemoAuthServiceTest {
    private final Firestore firestore = mock(Firestore.class);

    @Test
    void demoLoginIsDisabledByDefault() {
        DemoAuthService service = new DemoAuthService(firestore, false);

        assertThrows(DemoLoginDisabledException.class, () -> service.login("staff@scot.lk"));
        verifyNoInteractions(firestore);
    }

    @Test
    void rejectsAddressesOutsideTheUniversityDomainBeforeQueryingFirestore() {
        DemoAuthService service = new DemoAuthService(firestore, true);

        assertThrows(InvalidWorkEmailException.class, () -> service.login("staff@example.com"));
        verifyNoInteractions(firestore);
    }

    @Test
    void recognisesCurrentEmploymentStatusesFromTheImportedWorkbook() {
        org.junit.jupiter.api.Assertions.assertTrue(
                DemoAuthService.isCurrentEmployeeStatus("Permanent")
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                DemoAuthService.isCurrentEmployeeStatus("Probationary")
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                DemoAuthService.isCurrentEmployeeStatus("Intern")
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                DemoAuthService.isCurrentEmployeeStatus("Resigned")
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                DemoAuthService.isCurrentEmployeeStatus("Dismissed")
        );
    }
}
