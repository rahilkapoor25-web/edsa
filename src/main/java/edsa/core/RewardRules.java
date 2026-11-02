package edsa.core;

/**
 * What covering a colleague's duty is worth, and what it buys.
 *
 * <p>Every figure is configuration. The important relationship between them is not, though, and
 * it is worth saying why the defaults sit where they do: covering a duty earns ten points and
 * being excused one costs thirty, so three covers buy one exemption. A scheme that paid out as
 * much as it charged would be a way of doing a third of the work rather than a reward for doing
 * more than your share, and a scheme that paid more than it charged would empty the examination
 * hall. An office is free to change the ratio; it should do so knowing which way it tilts.
 *
 * @param pointsPerSubstitution what one completed substitute duty earns
 * @param pointsToSkipOneDuty   what being excused one future duty costs
 * @param validityDays          how long earned points last, or 0 for no expiry
 * @param maxRedemptionsPerSession how many duties one person may be excused in one session
 */
public record RewardRules(int pointsPerSubstitution,
                          int pointsToSkipOneDuty,
                          int validityDays,
                          int maxRedemptionsPerSession) {

    public RewardRules {
        if (pointsPerSubstitution < 0 || pointsToSkipOneDuty < 0) {
            throw new IllegalArgumentException("points cannot be negative");
        }
    }

    public static RewardRules defaults() {
        return new RewardRules(10, 30, 365, 1);
    }

    public boolean pointsExpire() {
        return validityDays > 0;
    }

    /** How many covers it takes to buy one exemption, for the explanation on the page. */
    public int coversPerExemption() {
        if (pointsPerSubstitution <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) pointsToSkipOneDuty / pointsPerSubstitution);
    }
}
