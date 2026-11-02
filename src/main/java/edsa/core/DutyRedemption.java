package edsa.core;

import java.time.LocalDateTime;

/**
 * A request to spend earned points on being excused one future duty.
 *
 * <p>A request and not a withdrawal, which is the whole design. Whether the duty can be given
 * up depends on who else is free at that hour, and that answer changes between asking on Monday
 * and deciding on Wednesday, so it is settled at the decision and not at the request.
 */
public record DutyRedemption(long id, String facultyId, String slotId, String roomId, String paperCode,
                             LocalDateTime startsAt, int pointsSpent, RedemptionStatus status,
                             String reason, LocalDateTime requestedAt,
                             String decidedBy, LocalDateTime decidedAt, String outcome) {

    public static DutyRedemption request(String facultyId, String slotId, String roomId, String paperCode,
                                         LocalDateTime startsAt, int pointsSpent, String reason,
                                         LocalDateTime now) {
        return new DutyRedemption(0, facultyId, slotId, roomId, paperCode, startsAt, pointsSpent,
                RedemptionStatus.PENDING, reason, now, null, null, null);
    }

    /**
     * Whether this account may decide it. Not the person asking, whatever role they hold.
     */
    public boolean mayBeDecidedBy(String accountFacultyId) {
        return status.isPending() && (accountFacultyId == null || !accountFacultyId.equals(facultyId));
    }
}
