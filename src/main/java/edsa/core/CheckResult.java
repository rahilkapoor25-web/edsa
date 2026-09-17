package edsa.core;

/** What one rule made of a plan: whether it holds, and how many things break it if not. */
public record CheckResult(String constraintName, boolean hard, boolean satisfied, int breaches, int penalty) {
}
