package edsa.core;

/** One rule a plan breaks. */
public record Violation(String constraintName, boolean hard, int penalty) {

    @Override
    public String toString() {
        return (hard ? "hard" : "soft") + ": " + constraintName + (hard ? "" : " (+" + penalty + ")");
    }
}
