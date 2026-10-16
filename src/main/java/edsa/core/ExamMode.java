package edsa.core;

/**
 * The kind of examination being scheduled, and how long one sitting of it lasts.
 *
 * <p>The duration is the only thing that differs between modes: one scheduling algorithm
 * serves all three, and changing the mode changes every computed end time.
 *
 * <p>The mode is the authority on how long a sitting runs. A paper may carry its own length
 * for the longer modes, but a Quiz is a fixed ten minutes: a stale {@code Duration Minutes}
 * value left in a workbook can never stretch one, because {@link #minutesFor(Integer)} is the
 * single place that decides and every other component asks it.
 */
public enum ExamMode {

    QUIZ("Quiz", 10),
    MIDSEM("Midsem", 120),
    ENDSEM("Endsem", 180);

    /**
     * The longest a declared paper may ask for. Five hours is longer than any sitting the
     * office runs and still inside one working day, so anything beyond it is a data error
     * rather than a preference.
     */
    public static final int MAX_OVERRIDE_MINUTES = 300;

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

    /**
     * Whether a paper may set its own length under this mode.
     *
     * <p>False for {@link #QUIZ}: every quiz is the same ten minutes, so there is nothing to
     * override. True for the longer modes, where a paper genuinely may run short or long.
     */
    public boolean allowsDurationOverride() {
        return this != QUIZ;
    }

    /** Whether this mode accepts that declared length, rather than falling back to its own. */
    public boolean permits(Integer overrideMinutes) {
        return allowsDurationOverride()
                && overrideMinutes != null
                && overrideMinutes > 0
                && overrideMinutes <= MAX_OVERRIDE_MINUTES;
    }

    /**
     * How long one sitting of this mode runs, given what a paper declared for itself.
     *
     * <p>This is the authoritative answer, and the only one: the mode's own duration unless
     * the mode accepts the declared length. A refused value is not an error — the sitting is
     * simply timed by the mode — but {@link Exam#overrideIgnoredUnder(ExamMode)} reports it so
     * the pages can say the workbook value was not used.
     */
    public int minutesFor(Integer overrideMinutes) {
        return permits(overrideMinutes) ? overrideMinutes : defaultMinutes;
    }

    /** The duration as it reads on screen: "10 min", "2 h", "3 h". */
    public String durationLabel() {
        return Durations.label(defaultMinutes);
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
