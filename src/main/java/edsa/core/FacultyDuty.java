package edsa.core;

/**
 * One invigilation duty as a page reads it: when, which paper, and which room.
 *
 * <p>The room comes from an actual {@link Assignment} in the duty roster. Nothing here is
 * inferred from a name or guessed from a department, so a duty shown on a page is a duty the
 * allocator really gave out.
 */
public record FacultyDuty(Faculty member, ExamSlot slot, Room room, int seatedInRoom) {

    /**
     * The role, as far as the data actually distinguishes one.
     *
     * <p>Only invigilation is allocated, so this says whether they are the senior in the room
     * rather than inventing a hierarchy the system does not model.
     */
    public String role() {
        return member.isSenior() ? "Senior invigilator" : "Invigilator";
    }
}
