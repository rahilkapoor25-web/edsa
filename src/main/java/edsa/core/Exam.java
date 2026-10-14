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

    public Exam(String id, String subjectCode, String subjectName, LocalDate date,
                LocalTime permittedStart, Integer durationOverrideMinutes) {
        this.id = id;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.date = date;
        this.permittedStart = permittedStart;
        this.durationOverrideMinutes = durationOverrideMinutes;
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

    /** How long this exam runs under the given mode, honouring a validated override. */
    public int durationUnder(ExamMode mode) {
        return durationOverrideMinutes != null ? durationOverrideMinutes : mode.defaultMinutes();
    }

    @Override
    public String toString() {
        return subjectCode + " on " + date;
    }
}
