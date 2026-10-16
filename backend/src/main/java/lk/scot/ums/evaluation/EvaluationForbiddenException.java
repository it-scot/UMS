package lk.scot.ums.evaluation;

public class EvaluationForbiddenException extends RuntimeException {
    public EvaluationForbiddenException() {
        super("The current user is not authorised for this evaluation action.");
    }
}
