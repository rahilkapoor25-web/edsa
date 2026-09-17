package edsa.core;

/**
 * A rule that decides whether a plan is allowed at all. A plan breaking one is thrown away
 * immediately, so hard rules carry no penalty points.
 */
public interface HardConstraint extends Constraint {

    @Override
    default int penalty(ExamPlan plan) {
        return 0;
    }
}
