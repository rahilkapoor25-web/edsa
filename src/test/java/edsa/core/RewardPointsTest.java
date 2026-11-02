package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Adding up a points balance, and what each of the four figures is for. */
class RewardPointsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);

    private static PointsEntry earned(int points, LocalDateTime expiresAt) {
        return new PointsEntry(1, "F01", PointsKind.EARNED, points, 1L, null, NOW, "admin", expiresAt, null);
    }

    private static PointsEntry of(PointsKind kind, int points) {
        return new PointsEntry(1, "F01", kind, points, null, null, NOW, "admin", null, null);
    }

    @Test
    void anEmptyLedgerIsNotAnError() {
        PointsBalance balance = PointsBalance.from("F01", List.of(), 0);

        assertTrue(balance.isEmpty());
        assertEquals(0, balance.available());
        assertFalse(balance.canAfford(1));
    }

    @Test
    void aBalanceIsTheSumOfItsRows() {
        PointsBalance balance = PointsBalance.from("F01",
                List.of(earned(10, null), earned(10, null), of(PointsKind.REDEEMED, -30)), 0);

        assertEquals(20, balance.earned());
        assertEquals(30, balance.redeemed());
        assertEquals(0, balance.available(), "a balance is never negative, however the rows add up");
    }

    /**
     * The reason "available" exists as its own figure. Without the hold, somebody with thirty
     * points could ask to be excused three duties and all three would look affordable right up
     * to the moment the third was approved.
     */
    @Test
    void pointsCommittedToAnUndecidedRequestCannotBeSpentTwice() {
        PointsBalance balance = PointsBalance.from("F01", List.of(earned(30, null)), 30);

        assertEquals(30, balance.earned());
        assertEquals(30, balance.pending());
        assertEquals(0, balance.available());
        assertFalse(balance.canAfford(30), "the same thirty points must not buy two exemptions");
    }

    @Test
    void anExpiredCreditLeavesTheBalanceButNotTheHistory() {
        PointsBalance balance = PointsBalance.from("F01",
                List.of(earned(10, NOW.minusDays(1)), of(PointsKind.EXPIRED, -10)), 0);

        assertEquals(10, balance.earned(), "the award is still in the history");
        assertEquals(10, balance.expired());
        assertEquals(0, balance.available());
    }

    @Test
    void aCorrectionCountsEitherWay() {
        PointsBalance up = PointsBalance.from("F01", List.of(of(PointsKind.ADJUSTED, 15)), 0);
        PointsBalance down = PointsBalance.from("F01",
                List.of(earned(20, null), of(PointsKind.ADJUSTED, -5)), 0);

        assertEquals(15, up.available());
        assertEquals(15, down.available());
    }

    @Test
    void aCreditKnowsWhetherItHasLapsed() {
        assertTrue(earned(10, NOW.minusMinutes(1)).hasExpiredBy(NOW));
        assertFalse(earned(10, NOW.plusMinutes(1)).hasExpiredBy(NOW));
        assertFalse(earned(10, null).hasExpiredBy(NOW), "a credit with no expiry never lapses");
        assertFalse(of(PointsKind.REDEEMED, -10).hasExpiredBy(NOW), "a debit cannot expire");
    }

    @Test
    void theDefaultRulesChargeMoreThanTheyPay() {
        RewardRules rules = RewardRules.defaults();

        assertTrue(rules.pointsToSkipOneDuty() > rules.pointsPerSubstitution(),
                "a scheme that paid as much as it charged would be a way of doing less work");
        assertEquals(3, rules.coversPerExemption());
        assertTrue(rules.pointsExpire());
    }

    @Test
    void theSignIsWhatTheHistoryShows() {
        assertEquals("+10", earned(10, null).signedPoints());
        assertEquals("-30", of(PointsKind.REDEEMED, -30).signedPoints());
    }
}
