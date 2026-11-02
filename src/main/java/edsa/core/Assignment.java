package edsa.core;

/**
 * One invigilation duty: a faculty member in a room for a slot, and where the duty came from.
 *
 * <p>The origin is part of the duty rather than kept beside it, because every copy of a roster
 * — the repaired one, the exported one, the one a page is rendering — needs to know which duties
 * a person has stood behind. Two assignments for the same person, room and slot are the same
 * duty whatever their origin, so {@link #sameDuty(Assignment)} is what the code compares when it
 * asks "is this duty already here?" and the record's own equality is left alone for the rest.
 */
public record Assignment(String slotId, String roomId, String facultyId, DutyOrigin origin) {

    public Assignment(String slotId, String roomId, String facultyId) {
        this(slotId, roomId, facultyId, DutyOrigin.RECOMMENDED);
    }

    public Assignment {
        origin = origin == null ? DutyOrigin.RECOMMENDED : origin;
    }

    /** The same duty with a different origin, for approving or replacing one. */
    public Assignment as(DutyOrigin newOrigin) {
        return new Assignment(slotId, roomId, facultyId, newOrigin);
    }

    /** Whether this is the same duty as the other, ignoring where each came from. */
    public boolean sameDuty(Assignment other) {
        return other != null
                && slotId.equals(other.slotId())
                && roomId.equals(other.roomId())
                && facultyId.equals(other.facultyId());
    }
}
