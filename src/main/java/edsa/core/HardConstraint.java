package edsa.core;

/**
 * A rule that decides whether a plan is allowed at all. A plan breaking one is thrown away
 * immediately, so hard rules carry no penalty points.
 *
 * <p>Implement {@link #breaches(ExamPlan)}: listing what is wrong is the only thing a hard rule
 * has to do, and the verdict follows from it.
 */
public interface HardConstraint extends Constraint {

    @Override
    default int penalty(ExamPlan plan) {
        return 0;
    }
}
