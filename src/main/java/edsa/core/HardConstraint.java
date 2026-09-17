package edsa.core;

/**
 * A rule that decides whether a plan is allowed at all. A plan breaking one is thrown away
 * immediately, so hard rules carry no penalty points.
 *
 * <p>Implement {@link #breaches(ExamPlan)}: counting is the only thing a hard rule has to do,
 * and both the verdict and the penalty follow from it.
 */
public interface HardConstraint extends Constraint {

    @Override
    int breaches(ExamPlan plan);

    @Override
    default boolean isSatisfied(ExamPlan plan) {
        return breaches(plan) == 0;
    }

    @Override
    default int penalty(ExamPlan plan) {
        return 0;
    }
}
