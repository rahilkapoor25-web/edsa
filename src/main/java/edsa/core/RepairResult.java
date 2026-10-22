package edsa.core;

import java.util.List;

/**
 * What covering one absence cost: the repaired plan, the duties that changed hands, and any
 * that nobody could take.
 *
 * <p>A repair is only safe to publish when {@link #isComplete()} holds. An incomplete repair
 * is still returned, because knowing which room has no invigilator is more useful than a
 * refusal.
 */
public record RepairResult(ExamPlan plan, Faculty absent, List<DutyChange> changes, List<String> uncovered) {

    public boolean isComplete() {
        return uncovered.isEmpty();
    }

    public boolean changedAnything() {
        return !changes.isEmpty();
    }
}
