package edsa.core;

/**
 * The kind of examination being scheduled, and how long one sitting of it lasts.
 *
 * <p>The duration is the only thing that differs between modes: one scheduling algorithm
 * serves all three, and changing the mode changes every computed end time.
 */
public enum ExamMode {

    QUIZ("Quiz", 10),
    MIDSEM("Midsem", 120),
    ENDSEM("Endsem", 180);

    private final String label;
    private final int defaultMinutes;

    ExamMode(String label, int defaultMinutes) {
        this.label = label;
        this.defaultMinutes = defaultMinutes;
    }

    public String label() {
        return label;
    }

    public int defaultMinutes() {
        return defaultMinutes;
    }

    public static ExamMode of(String name) {
        for (ExamMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name) || mode.label.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown exam mode '" + name + "', expected one of QUIZ, MIDSEM, ENDSEM");
    }
}
