package edsa.core;

import java.util.List;

/** One rule a plan breaks, and what exactly broke it. */
public record Violation(String constraintName, boolean hard, int penalty, List<String> details) {

    @Override
    public String toString() {
        return (hard ? "hard" : "soft") + ": " + constraintName + " (" + details.size() + ")";
    }
}
