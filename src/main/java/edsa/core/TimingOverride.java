package edsa.core;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A change to when one paper runs, made by an authorised person rather than declared in a
 * workbook.
 *
 * <p>Any part may be left out, and left out means "leave this as it was". A coordinator moving
 * a paper half an hour later should not have to restate its date and its length to do it, and
 * requiring them to would turn every small change into three chances to mistype something.
 *
 * @param examId   the paper this re-times
 * @param date     the new date, or null to keep the declared one
 * @param start    the new start time, or null to let the scheduler keep choosing
 * @param minutes  the new length, or null to keep the mode's
 */
public record TimingOverride(String examId, LocalDate date, LocalTime start, Integer minutes) {

    public TimingOverride {
        if (examId == null || examId.isBlank()) {
            throw new IllegalArgumentException("a timing override needs an exam id");
        }
        examId = examId.trim();
    }

    /** Whether this override actually asks for anything. */
    public boolean changesAnything() {
        return date != null || start != null || minutes != null;
    }

    /** How this change reads on the timing page and in the audit history. */
    public String describe(ExamMode mode) {
        StringBuilder text = new StringBuilder();
        if (date != null) {
            text.append(date);
        }
        if (start != null) {
            text.append(text.isEmpty() ? "" : " ").append(start);
        }
        if (minutes != null) {
            text.append(text.isEmpty() ? "" : " for ").append(Durations.label(mode.minutesForApproved(minutes)));
        }
        return text.isEmpty() ? "no change" : text.toString();
    }
}
