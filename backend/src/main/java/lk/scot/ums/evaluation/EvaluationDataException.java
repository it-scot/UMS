package lk.scot.ums.evaluation;

public class EvaluationDataException extends RuntimeException {
    public EvaluationDataException(Throwable cause) {
        super("Evaluation data could not be read or written.", cause);
    }
}
