package edsa.core;

import java.time.LocalDateTime;

/**
 * One row of the points ledger: signed, attributed and never edited afterwards.
 *
 * <p>Signed, because a balance that is the sum of its rows cannot disagree with its history.
 * Attributed, because every row says who caused it. And never edited, because a correction is
 * another row — that is what makes the history worth showing a professor who disputes their
 * balance.
 *
 * @param points        positive for a credit, negative for a debit
 * @param substitutionId the substitution this credits, or null
 * @param redemptionId  the redemption this pays for, or null
 * @param expiresAt     when this credit lapses if unused, or null for one that does not
 */
public record PointsEntry(long id, String facultyId, PointsKind kind, int points,
                          Long substitutionId, Long redemptionId,
                          LocalDateTime createdAt, String createdBy,
                          LocalDateTime expiresAt, String note) {

    public boolean isCredit() {
        return points > 0;
    }

    /** Whether this credit has lapsed by the given moment. A debit never expires. */
    public boolean hasExpiredBy(LocalDateTime moment) {
        return isCredit() && expiresAt != null && !moment.isBefore(expiresAt);
    }

    public String signedPoints() {
        return points > 0 ? "+" + points : String.valueOf(points);
    }
}
