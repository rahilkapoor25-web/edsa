package edsa.core;

/**
 * A preference. A plan that does not meet it is still legal, but earns penalty points, and a
 * lower total is a better plan.
 */
public interface SoftConstraint extends Constraint {

    @Override
    default boolean isSatisfied(ExamPlan plan) {
        return penalty(plan) == 0;
    }
}
