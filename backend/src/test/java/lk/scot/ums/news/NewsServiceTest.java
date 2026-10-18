package lk.scot.ums.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.cloud.firestore.Firestore;
import lk.scot.ums.profile.EmployeeProfile;
import lk.scot.ums.auth.VerifiedSession;
import lk.scot.ums.auth.VerifiedSessionService;
import lk.scot.ums.evaluation.EvaluationForbiddenException;
import org.junit.jupiter.api.Test;

class NewsServiceTest {
    private final Firestore firestore = mock(Firestore.class);
    private final VerifiedSessionService sessions = mock(VerifiedSessionService.class);
    private final NewsService service = new NewsService(firestore, sessions);

    @Test
    void nonAdminsCannotPublishNews() {
        EmployeeProfile staff = new EmployeeProfile(
                "staff-1", "Regular Staff", "Staff", "", "Lecturer", "Computing",
                "lecturer@scot.lk", "", "Permanent", "Staff", "", "", ""
        );
        when(sessions.verify("staff-token")).thenReturn(new VerifiedSession(staff, false, false));

        assertThrows(
                EvaluationForbiddenException.class,
                () -> service.create(
                        "staff-token",
                        new NewsRequest(
                                "Staff Meeting", "Meeting summary", "Meeting content",
                                "Events", false, ""
                        )
                )
        );
        verifyNoInteractions(firestore);
    }

    @Test
    void hrAndAdminAreRecognizedAsNewsManagers() {
        EmployeeProfile hr = new EmployeeProfile(
                "hr-1", "HR Admin", "HR", "Mr.", "Senior HR Manager", "HR",
                "hr@scot.lk", "", "Permanent", "Administrator", "", "", ""
        );
        when(sessions.verify("hr-token")).thenReturn(new VerifiedSession(hr, true, false));

        EmployeeProfile admin = new EmployeeProfile(
                "admin-1", "University Admin", "Admin", "Mr.", "Administrator", "Admin",
                "admin@scot.lk", "", "Permanent", "Administrator", "", "", ""
        );
        when(sessions.verify("admin-token")).thenReturn(new VerifiedSession(admin, true, false));

        assertTrue(service.isAuthorizedNewsManager("hr-token"));
        assertTrue(service.isAuthorizedNewsManager("admin-token"));
    }

    @Test
    void fallbackStaticNewsContainsRealisticAnnouncements() {
        EmployeeProfile staff = new EmployeeProfile(
                "staff-1", "Regular Staff", "Staff", "", "Lecturer", "Computing",
                "lecturer@scot.lk", "", "Permanent", "Staff", "", "", ""
        );
        when(sessions.verify("staff-token")).thenReturn(new VerifiedSession(staff, false, false));

        var list = service.list("staff-token");
        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(n -> n.title().contains("Convocation")));
        assertTrue(list.stream().anyMatch(NewsArticle::isSpecial));
    }
}
