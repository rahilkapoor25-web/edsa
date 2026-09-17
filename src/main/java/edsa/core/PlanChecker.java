package edsa.core;

import java.util.List;

/** Runs every registered rule over a plan and lists what is broken. */
public final class PlanChecker {

    private final List<Constraint> constraints;

    public PlanChecker(List<Constraint> constraints) {
        this.constraints = List.copyOf(constraints);
    }

    /** The rules in force. Adding a rule is one line here. */
    public static PlanChecker standard() {
        return new PlanChecker(List.of(
                new RoomCapacityConstraint(),
                new DoubleBookingConstraint()));
    }

    public List<Constraint> constraints() {
        return constraints;
    }

    /** Every rule and how the plan fared against it, in registration order. */
    public List<CheckResult> inspect(ExamPlan plan) {
        return constraints.stream()
                .map(constraint -> new CheckResult(
                        constraint.name(),
                        constraint instanceof HardConstraint,
                        constraint.isSatisfied(plan),
                        constraint.breaches(plan),
                        constraint.penalty(plan)))
                .toList();
    }

    /** Only the rules the plan breaks. */
    public List<Violation> check(ExamPlan plan) {
        return inspect(plan).stream()
                .filter(result -> !result.satisfied())
                .map(result -> new Violation(result.constraintName(), result.hard(), result.penalty()))
                .toList();
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
