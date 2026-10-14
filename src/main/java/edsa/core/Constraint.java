package edsa.core;

import java.util.List;

/**
 * One rule the examination office follows.
 *
 * <p>Implementations are stateless with respect to the plan: the plan being judged is passed
 * in, so one registered instance can check any plan.
 */
public interface Constraint {

    /** How this rule is named in a violation list. */
    String name();

    /**
     * One message per separate thing that breaks this rule, each naming what is wrong and
     * where, so the coordinator can act on it. Empty when the plan satisfies the rule.
     */
    List<String> breaches(ExamPlan plan);

    default boolean isSatisfied(ExamPlan plan) {
        return breaches(plan).isEmpty();
    }

    /** Penalty points this plan earns. Lower is better; zero means nothing to complain about. */
    int penalty(ExamPlan plan);
}
