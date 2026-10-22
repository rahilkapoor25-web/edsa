package edsa.core;

/**
 * The kind of examination being scheduled, and how long one sitting of it lasts.
 *
 * <p>The duration is the only thing that differs between modes: one scheduling algorithm
 * serves all three, and changing the mode changes every computed end time.
 *
 * <p>The mode is the sole authority on how long a sitting runs. Every Quiz is ten minutes,
 * every Midsem two hours and every Endsem three — a {@code Duration Minutes} value left in a
 * workbook cannot change any of them, because {@link #minutesFor(Integer)} is the single
 * place that decides and every other component asks it.
 *
 * <p>Papers used to be allowed their own length under the two longer modes. That turned out
 * to be a trap rather than a feature: a value left over from an earlier session quietly
 * produced a ninety-minute Endsem paper sitting in a timetable headed "3 h". A declared
 * length is now reported as ignored instead of applied — see
 * {@link Exam#overrideIgnoredUnder(ExamMode)} — so the figure in the workbook is visible
 * without being obeyed.
 */
public enum ExamMode {

    QUIZ("Quiz", 10),
    MIDSEM("Midsem", 120),
    ENDSEM("Endsem", 180);

    /**
     * The largest {@code Duration Minutes} the importer will accept in a workbook.
     *
     * <p>No mode acts on the value any more, but a nonsense one is still worth refusing at
     * import so the coordinator fixes their spreadsheet rather than wondering why a figure in
     * it is being ignored.
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
     * <p>False for every mode. An examination of a given kind runs for the same length as
     * every other examination of that kind, which is the whole reason the office picks a mode
     * rather than typing times in. Kept as a method, rather than deleted, because the rest of
     * the system asks the mode rather than assuming — so reinstating per-paper lengths later
     * would be a change here and nowhere else.
     */
    public boolean allowsDurationOverride() {
        return false;
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
     * <p>This is the authoritative answer, and the only one. Today no mode accepts a declared
     * length, so this is always the mode's own duration; the parameter is still taken so that
     * every caller keeps asking rather than reading {@link #defaultMinutes()} directly and
     * drifting apart. A refused value is not an error — the sitting is simply timed by the
     * mode — but {@link Exam#overrideIgnoredUnder(ExamMode)} reports it so the pages can say
     * the workbook value was not used.
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
