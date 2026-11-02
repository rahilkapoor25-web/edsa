package edsa.core;

import java.util.List;

/**
 * A professor's points, in the four figures that answer different questions.
 *
 * <p>Available is the only one that can be spent, and it is not earned minus redeemed. Points
 * committed to a redemption nobody has decided yet are held: without that, somebody with thirty
 * points could ask to be excused three duties on the same afternoon, and all three requests
 * would look affordable right up to the moment the third was approved.
 *
 * @param earned   every credit ever given
 * @param redeemed every credit spent on an approved exemption
 * @param expired  credits that lapsed unused
 * @param pending  credits held against requests awaiting a decision
 */
public record PointsBalance(String facultyId, int earned, int redeemed, int expired, int pending) {

    public static PointsBalance empty(String facultyId) {
        return new PointsBalance(facultyId, 0, 0, 0, 0);
    }

    /**
     * Builds a balance from the ledger rows and the points held by undecided requests.
     *
     * <p>Adjustments are folded into earned when positive and into redeemed when negative,
     * because a correction is only ever an addition to or a subtraction from what somebody has,
     * and giving it a column of its own would mean four figures nobody could add up.
     */
    public static PointsBalance from(String facultyId, List<PointsEntry> entries, int pending) {
        int earned = 0;
        int redeemed = 0;
        int expired = 0;
        for (PointsEntry entry : entries) {
            switch (entry.kind()) {
                case EARNED -> earned += entry.points();
                case REDEEMED -> redeemed += Math.abs(entry.points());
                case EXPIRED -> expired += Math.abs(entry.points());
                case ADJUSTED -> {
                    if (entry.points() >= 0) {
                        earned += entry.points();
                    } else {
                        redeemed += Math.abs(entry.points());
                    }
                }
            }
        }
        return new PointsBalance(facultyId, earned, redeemed, expired, pending);
    }

    /** What may actually be spent now. Never negative, however the rows add up. */
    public int available() {
        return Math.max(0, earned - redeemed - expired - pending);
    }

    public boolean canAfford(int cost) {
        return available() >= cost;
    }

    public boolean isEmpty() {
        return earned == 0 && redeemed == 0 && expired == 0 && pending == 0;
    }
}
