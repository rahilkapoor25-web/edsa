package edsa.core;

import java.time.LocalDateTime;

/**
 * One professor's recorded absence, and where its review stands.
 *
 * <p>Recording a request is not the same as granting it. The application does not judge
 * whether somebody is ill: it stores what was reported, waits for an authorised person to
 * confirm or reject it, and only then — see {@link #mayReassign()} — lets a duty move. A
 * request whose deadline passes without a decision becomes
 * {@link #isOverdueForReview(LocalDateTime) overdue for review}; it is never treated as
 * granted, because silence is not approval.
 *
 * <p>The faculty member's name is kept beside their id so an old request still reads sensibly
 * after a later workbook import changes the roll.
 */
public record LeaveRequest(long id,
                           String facultyId,
                           String facultyName,
                           AbsenceWindow window,
                           String reason,
                           LeaveStatus status,
                           LocalDateTime requestedAt,
                           LocalDateTime confirmBy,
                           LocalDateTime decidedAt,
                           String outcome) {

    public LeaveRequest {
        if (facultyId == null || facultyId.isBlank()) {
            throw new IllegalArgumentException("a leave request needs the professor it is about");
        }
        if (window == null) {
            throw new IllegalArgumentException("a leave request needs an absence period");
        }
        if (status == null) {
            throw new IllegalArgumentException("a leave request needs a status");
        }
        if (confirmBy == null) {
            throw new IllegalArgumentException("a leave request needs a confirmation deadline");
        }
    }

    /** A new request, pending review, to be confirmed by the given deadline. */
    public static LeaveRequest pending(String facultyId, String facultyName, AbsenceWindow window,
                                       String reason, LocalDateTime requestedAt, LocalDateTime confirmBy) {
        return new LeaveRequest(0, facultyId, facultyName, window, reason, LeaveStatus.PENDING,
                requestedAt, confirmBy, null, null);
    }

    /** Whether duties may be taken off this professor: only a confirmed absence counts. */
    public boolean mayReassign() {
        return status.mayReassign();
    }

    /**
     * Whether this professor is to be treated as away for the purposes of covering a sitting.
     * A confirmed request still awaiting reassignment counts, and so does one already acted on.
     */
    public boolean keepsThemAway() {
        return status == LeaveStatus.CONFIRMED || status == LeaveStatus.COMPLETED;
    }

    /**
     * Nobody decided in time.
     *
     * <p>This is reported so the request can be chased, never resolved: an overdue request is
     * still pending and still changes nothing.
     */
    public boolean isOverdueForReview(LocalDateTime now) {
        return status.isOpen() && now.isAfter(confirmBy);
    }

    /** True when this sitting falls inside the absence, so its duty needs covering. */
    public boolean affects(ExamSlot slot) {
        return window.covers(slot);
    }

    /** This professor's absence as the repairer understands it. */
    public Absence asAbsence() {
        return new Absence(facultyId, window);
    }

    public LeaveRequest decided(LeaveStatus decision, LocalDateTime when, String outcome) {
        return new LeaveRequest(id, facultyId, facultyName, window, reason, decision,
                requestedAt, confirmBy, when, outcome);
    }
}
