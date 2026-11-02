package edsa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Who invigilates what.
 *
 * <p>A roster holds both the allocator's recommendations and the duties people have decided,
 * told apart by each assignment's {@link DutyOrigin}. {@link #decide(DutyDecision)} is how a
 * decision is laid over a recommendation, and it is applied after every recompute so that
 * reloading the workbook does not silently undo a coordinator's work.
 */
public final class DutyRoster {

    private final List<Assignment> assignments = new ArrayList<>();

    public void assign(Assignment assignment) {
        assignments.add(assignment);
    }

    /** Takes a duty back off, so a solver can try a candidate and think better of it. */
    public void remove(Assignment assignment) {
        assignments.remove(assignment);
    }

    public List<Assignment> all() {
        return Collections.unmodifiableList(assignments);
    }

    public List<Assignment> forSlot(String slotId) {
        return assignments.stream().filter(a -> a.slotId().equals(slotId)).toList();
    }

    public List<Assignment> forFaculty(String facultyId) {
        return assignments.stream().filter(a -> a.facultyId().equals(facultyId)).toList();
    }

    /** The duty for one room at one sitting, when there is one. */
    public java.util.Optional<Assignment> forSitting(String slotId, String roomId) {
        return assignments.stream()
                .filter(a -> a.slotId().equals(slotId) && a.roomId().equals(roomId))
                .findFirst();
    }

    /**
     * Puts a decided duty in place of whatever was recommended for that room.
     *
     * <p>A decision for a room this plan does not use is applied anyway rather than dropped. The
     * room may be missing because the recompute that produced this roster seated the cohort
     * differently, and silently discarding the decision would lose a person's work without
     * telling them; a duty for an unused room is visible on the page and harmless in the plan.
     */
    public void decide(DutyDecision decision) {
        forSitting(decision.slotId(), decision.roomId()).ifPresent(assignments::remove);
        assignments.add(new Assignment(decision.slotId(), decision.roomId(),
                decision.facultyId(), decision.origin()));
    }

    /** Applies every decision, in order, over the recommendations already here. */
    public void applyAll(List<DutyDecision> decisions) {
        decisions.forEach(this::decide);
    }

    /** How many duties a person stands behind, for the "N of M approved" counts on the pages. */
    public long decidedCount() {
        return assignments.stream().filter(a -> a.origin().decidedByPerson()).count();
    }

    /** Moves one duty to somebody else, for a substitution or an approved exemption. */
    public void reassign(String slotId, String roomId, String newFacultyId, DutyOrigin origin) {
        forSitting(slotId, roomId).ifPresent(assignments::remove);
        assignments.add(new Assignment(slotId, roomId, newFacultyId, origin));
    }
}
