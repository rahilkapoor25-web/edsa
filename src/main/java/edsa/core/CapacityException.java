package edsa.core;

/** The rooms cannot hold everyone who has to sit a paper. */
public class CapacityException extends RuntimeException {

    public CapacityException(String message) {
        super(message);
    }
}
