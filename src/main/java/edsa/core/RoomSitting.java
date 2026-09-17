package edsa.core;

import java.util.List;

/** One room, sitting one paper, at one time: who is seated where, and who is watching. */
public record RoomSitting(ExamSlot slot, Room room, List<PlacedStudent> students, List<Faculty> invigilators) {

    public int seated() {
        return students.size();
    }
}
