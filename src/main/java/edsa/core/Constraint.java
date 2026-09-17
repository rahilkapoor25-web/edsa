package edsa.core;

/**
 * One rule the examination office follows.
 *
 * <p>Implementations are stateless and are registered once in {@link PlanChecker}; the plan
 * being judged is passed in, so the same instance can check any plan.
 */
public interface Constraint {

    /** How this rule is named in a violation list. */
    String name();

    boolean isSatisfied(ExamPlan plan);

    /** Penalty points this plan earns. Lower is better; zero means nothing to complain about. */
    int penalty(ExamPlan plan);
}
