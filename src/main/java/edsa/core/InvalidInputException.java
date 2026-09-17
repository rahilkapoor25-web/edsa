package edsa.core;

/** A row in one of the four input files could not be understood. */
public class InvalidInputException extends RuntimeException {

    private final String fileName;
    private final int lineNumber;

    public InvalidInputException(String fileName, int lineNumber, String problem) {
        super(fileName + " line " + lineNumber + ": " + problem);
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
