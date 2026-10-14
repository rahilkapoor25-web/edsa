package edsa.core;

import java.util.List;

/**
 * A preference. A plan that does not meet it is still legal, but earns penalty points, and a
 * lower total is a better plan.
 */
public interface SoftConstraint extends Constraint {

    @Override
    default List<String> breaches(ExamPlan plan) {
        int penalty = penalty(plan);
        return penalty == 0 ? List.of() : List.of(name() + " costs " + penalty + " penalty points");
    }
}
