package edsa.core;

import java.util.ArrayList;
import java.util.List;

/** Runs every registered rule over a finished plan and lists exactly what is broken. */
public final class PlanChecker {

    private final List<Constraint> constraints;

    public PlanChecker(List<Constraint> constraints) {
        this.constraints = List.copyOf(constraints);
    }

    /** The rules in force. Adding a rule is one line here. */
    public static PlanChecker using(SchedulingConfig config) {
        return new PlanChecker(List.of(
                new ExamDurationConstraint(),
                new RoomCapacityConstraint(config),
                new RoomOverlapConstraint(),
                new StudentConflictConstraint(),
                new FacultyConflictConstraint()));
    }

    public static PlanChecker standard() {
        return using(SchedulingConfig.defaults());
    }

    public List<Constraint> constraints() {
        return constraints;
    }

    /** Every rule and how the plan fared against it, in registration order. */
    public List<CheckResult> inspect(ExamPlan plan) {
        List<CheckResult> results = new ArrayList<>();
        for (Constraint constraint : constraints) {
            List<String> breaches = constraint.breaches(plan);
            results.add(new CheckResult(constraint.name(), constraint instanceof HardConstraint,
                    breaches.isEmpty(), breaches, constraint.penalty(plan)));
        }
        return results;
    }

    /** Only the rules the plan breaks. */
    public List<Violation> check(ExamPlan plan) {
        return inspect(plan).stream()
                .filter(result -> !result.satisfied())
                .map(result -> new Violation(result.constraintName(), result.hard(),
                        result.penalty(), result.breaches()))
                .toList();
    }

    /** Every actionable message from every broken rule, for reporting to the coordinator. */
    public List<String> problems(ExamPlan plan) {
        return inspect(plan).stream().flatMap(result -> result.breaches().stream()).toList();
    }

    /** True when no hard rule is broken, which is the only thing that makes a plan usable. */
    public boolean isLegal(ExamPlan plan) {
        return constraints.stream()
                .filter(HardConstraint.class::isInstance)
                .allMatch(constraint -> constraint.isSatisfied(plan));
    }

    /** Total penalty points. Lower is better. */
    public int score(ExamPlan plan) {
        return constraints.stream().mapToInt(constraint -> constraint.penalty(plan)).sum();
    }
}
