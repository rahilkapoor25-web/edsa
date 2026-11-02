package edsa.core;

import java.time.LocalDateTime;

/**
 * A coordinator's decision about one room's invigilator: the recommendation confirmed, or
 * somebody else put in its place.
 *
 * <p>Kept apart from the duty itself because the two have different lifetimes. A duty is part of
 * a plan and is thrown away and recomputed whenever the data or the mode changes; a decision is
 * a person's and outlives every recompute, which is the only reason approving a roster is worth
 * doing at all.
 */
public record DutyDecision(long id, String slotId, String roomId, String facultyId, DutyOrigin origin,
                           String decidedBy, LocalDateTime decidedAt, String note) {

    public static DutyDecision approve(String slotId, String roomId, String facultyId,
                                       String decidedBy, LocalDateTime now) {
        return new DutyDecision(0, slotId, roomId, facultyId, DutyOrigin.APPROVED, decidedBy, now, null);
    }

    public static DutyDecision assignByHand(String slotId, String roomId, String facultyId,
                                            String decidedBy, LocalDateTime now, String note) {
        return new DutyDecision(0, slotId, roomId, facultyId, DutyOrigin.MANUAL, decidedBy, now, note);
    }

    public boolean isFor(Assignment duty) {
        return duty != null && slotId.equals(duty.slotId()) && roomId.equals(duty.roomId());
    }
}
