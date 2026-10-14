package edsa.core;

import java.util.List;
import java.util.Map;

/**
 * When every paper is sat and which rooms are held for it.
 *
 * <p>The scheduler reserves the rooms so the allocator seats students into exactly the rooms
 * whose availability was checked, and the two cannot drift apart.
 */
public record Timetable(List<ExamSlot> slots, Map<String, List<String>> roomsBySlot) {

    public List<String> roomsFor(String slotId) {
        return roomsBySlot.getOrDefault(slotId, List.of());
    }

    public static Timetable empty() {
        return new Timetable(List.of(), Map.of());
    }
}
