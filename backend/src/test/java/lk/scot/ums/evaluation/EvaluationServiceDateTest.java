package lk.scot.ums.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EvaluationServiceDateTest {
    @Test
    void parsesCustomRoundDates() {
        assertEquals(LocalDate.of(2027, 2, 14), EvaluationService.date("2027-02-14", "startDate"));
    }

    @Test
    void rejectsMissingAndInvalidRoundDates() {
        assertThrows(EvaluationRequestException.class, () -> EvaluationService.date("", "startDate"));
        assertThrows(EvaluationRequestException.class, () -> EvaluationService.date("2027-02-30", "dueDate"));
        assertThrows(EvaluationRequestException.class, () -> EvaluationService.date("14/02/2027", "dueDate"));
    }
}
