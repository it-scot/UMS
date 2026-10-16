package lk.scot.ums.auth;

public class InvalidFirebaseSessionException extends RuntimeException {
    public InvalidFirebaseSessionException() {
        super("A verified Firebase session is required.");
    }

    public InvalidFirebaseSessionException(Throwable cause) {
        super("Firebase session verification failed.", cause);
    }
}
