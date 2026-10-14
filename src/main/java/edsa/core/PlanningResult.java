package edsa.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything one planning run produced: the plan, what could not be scheduled or allocated,
 * and the independent check of the finished plan.
 *
 * <p>A plan is only usable when nothing was left unplaced and every hard rule holds, so
 * {@link #isUsable()} is what the pages should ask before presenting it as a timetable.
 */
public record PlanningResult(ExamPlan plan, List<String> schedulingProblems,
                             List<String> allocationProblems, List<CheckResult> checks) {

    public boolean isUsable() {
        return schedulingProblems.isEmpty() && allocationProblems.isEmpty() && checks.stream()
                .filter(CheckResult::hard)
                .allMatch(CheckResult::satisfied);
    }

    /** Every reason this plan is not usable, in the order they were found. */
    public List<String> problems() {
        List<String> all = new ArrayList<>(schedulingProblems);
        all.addAll(allocationProblems);
        checks.stream().filter(check -> !check.satisfied()).forEach(check -> all.addAll(check.breaches()));
        return List.copyOf(all);
    }
}
