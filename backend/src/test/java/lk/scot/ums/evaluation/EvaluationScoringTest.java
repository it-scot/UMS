package lk.scot.ums.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EvaluationScoringTest {
    @Test
    void appliesThePolicyWeightsAndRoundsToTwoDecimalPlaces() {
        assertEquals(83.0, EvaluationScoring.finalScore(80, 100, 90));
        assertEquals(83.1, EvaluationScoring.finalScore(80.13, 100, 90));
    }

    @Test
    void followsThePolicyRatingThresholdsIncludingTheUnratedFortyPercentMark() {
        assertEquals("Outstanding", EvaluationScoring.rating(90));
        assertEquals("Meets Expectations", EvaluationScoring.rating(80));
        assertEquals("Meets Minimal Expectations", EvaluationScoring.rating(50));
        assertEquals("Needs Improvement", EvaluationScoring.rating(41));
        assertEquals("Unrated", EvaluationScoring.rating(40));
        assertEquals("Unsatisfactory", EvaluationScoring.rating(39.99));
    }
}
