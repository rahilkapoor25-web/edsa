package edsa.core;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * An exam as the examination office declares it: a paper on a date, optionally with a
 * permitted start time and a duration that overrides the mode's.
 *
 * <p>This is input, not a plan. {@link TimetableScheduler} turns it into an {@link ExamSlot}
 * with concrete times.
 */
public final class Exam {

    private final String id;
    private final String subjectCode;
    private final String subjectName;
    private final LocalDate date;
    private final LocalTime permittedStart;
    private final Integer durationOverrideMinutes;
    private final Integer approvedDurationMinutes;

    public Exam(String id, String subjectCode, String subjectName, LocalDate date,
                LocalTime permittedStart, Integer durationOverrideMinutes) {
        this(id, subjectCode, subjectName, date, permittedStart, durationOverrideMinutes, null);
    }

    public Exam(String id, String subjectCode, String subjectName, LocalDate date,
                LocalTime permittedStart, Integer durationOverrideMinutes,
                Integer approvedDurationMinutes) {
        this.id = id;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.date = date;
        this.permittedStart = permittedStart;
        this.durationOverrideMinutes = durationOverrideMinutes;
        this.approvedDurationMinutes = approvedDurationMinutes;
    }

    /**
     * The length an authorised person set for this paper, as opposed to the one its workbook
     * declared. Only a saved timing override puts a value here; the importer never does.
     */
    public Optional<Integer> getApprovedDurationMinutes() {
        return Optional.ofNullable(approvedDurationMinutes);
    }

    public boolean hasApprovedTiming() {
        return approvedDurationMinutes != null;
    }

    /**
     * This paper re-timed by an authorised person. A null argument leaves that part as it was,
     * so a change of start alone does not quietly clear a date or a duration.
     */
    public Exam retimed(LocalDate newDate, LocalTime newStart, Integer newDuration) {
        return new Exam(id, subjectCode, subjectName,
                newDate == null ? date : newDate,
                newStart == null ? permittedStart : newStart,
                durationOverrideMinutes,
                newDuration == null ? approvedDurationMinutes : newDuration);
    }

    public String getId() {
        return id;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public LocalDate getDate() {
        return date;
    }

    /** The start the office fixed, when it fixed one; otherwise the scheduler picks. */
    public Optional<LocalTime> getPermittedStart() {
        return Optional.ofNullable(permittedStart);
    }

    /** Minutes this paper runs for, when it differs from the mode's duration. */
    public Optional<Integer> getDurationOverrideMinutes() {
        return Optional.ofNullable(durationOverrideMinutes);
    }

    /**
     * How long this exam runs under the given mode.
     *
     * <p>The mode decides, not the workbook: a declared length is used only where the mode
     * accepts one, so a Quiz is ten minutes whatever the {@code Duration Minutes} column says.
     * A length an authorised person set through the timing page is honoured instead, within the
     * bounds the mode enforces — that is a signed decision rather than a stale cell.
     */
    public int durationUnder(ExamMode mode) {
        return approvedDurationMinutes == null
                ? mode.minutesFor(durationOverrideMinutes)
                : mode.minutesForApproved(approvedDurationMinutes);
    }

    /**
     * True when this paper declared a length the mode refuses, so the mode's own length is
     * used instead. The pages report it rather than silently timing the paper differently
     * from the figure in the workbook.
     */
    public boolean overrideIgnoredUnder(ExamMode mode) {
        return durationOverrideMinutes != null && !mode.permits(durationOverrideMinutes);
    }

    @Override
    public String toString() {
        return subjectCode + " on " + date;
    }
}
