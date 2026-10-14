package edsa.core;

import java.util.List;

/** What one rule made of a plan: whether it holds, and exactly what breaks it if not. */
public record CheckResult(String constraintName, boolean hard, boolean satisfied,
                          List<String> breaches, int penalty) {

    public int breachCount() {
        return breaches.size();
    }
}
