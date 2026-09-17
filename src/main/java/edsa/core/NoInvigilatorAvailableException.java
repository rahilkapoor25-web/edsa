package edsa.core;

/** Every faculty member would break a hard rule by taking this room, so nobody can. */
public class NoInvigilatorAvailableException extends RuntimeException {

    public NoInvigilatorAvailableException(String message) {
        super(message);
    }
}
