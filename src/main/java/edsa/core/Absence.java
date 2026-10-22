package edsa.core;

/**
 * Somebody away for a stretch of time, as the repairer needs to know it.
 *
 * <p>Deliberately smaller than {@link LeaveRequest}: when the repairer looks for a colleague
 * to take a room it only needs to know who is away and when, not how that was decided.
 */
public record Absence(String facultyId, AbsenceWindow window) {

    /** True when this person cannot invigilate that sitting. */
    public boolean rulesOut(String candidateId, ExamSlot slot) {
        return facultyId.equals(candidateId) && window.covers(slot);
    }
}
