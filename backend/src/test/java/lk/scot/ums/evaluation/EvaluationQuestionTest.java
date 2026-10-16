package lk.scot.ums.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluationQuestionTest {
    @Test
    void acceptsCustomRatingTablesAndExpectsOneAnswerPerCriterion() {
        Map<String, Object> table = Map.of(
                "type", "matrix",
                "prompt", "Rate this employee",
                "rows", List.of("Communication", "Teamwork"),
                "columns", List.of("Needs work", "Developing", "Meets", "Strong", "Excellent")
        );

        EvaluationService.validateTemplateQuestions(List.of("A legacy question", table));

        assertEquals(
                Set.of("0", "1.0", "1.1"),
                EvaluationService.expectedScoreKeys(List.of("A legacy question", table))
        );
    }

    @Test
    void rejectsRatingTablesWithoutFiveLabelsOrNamedCriteria() {
        Map<String, Object> invalidTable = Map.of(
                "type", "matrix",
                "prompt", "Rate this employee",
                "rows", List.of(""),
                "columns", List.of("Needs work", "Meets", "Excellent")
        );

        assertThrows(
                EvaluationRequestException.class,
                () -> EvaluationService.validateTemplateQuestions(List.of(invalidTable))
        );
    }
}
