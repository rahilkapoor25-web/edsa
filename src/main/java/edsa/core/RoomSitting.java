package edsa.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One room, sitting one paper, at one time: who is seated where, and who is watching. */
public record RoomSitting(ExamSlot slot, Room room, List<PlacedStudent> students, List<Faculty> invigilators) {

    public int seated() {
        return students.size();
    }

    /**
     * The seating as the room's grid, for showing or printing.
     *
     * <p>Positions come from the allocation; none is invented here. A position beyond the
     * room's capacity is marked as no seat at all rather than as an empty one, counting row by
     * row in the same order the allocator fills them, so the two always agree.
     */
    public SeatingGrid grid() {
        Map<String, PlacedStudent> byPosition = new LinkedHashMap<>();
        for (PlacedStudent placed : students) {
            byPosition.put(placed.row() + ":" + placed.column(), placed);
        }

        int columns = Math.max(1, room.getColumnCount());
        List<List<SeatCell>> rows = new ArrayList<>();
        for (int row = 1; row <= Math.max(1, room.getRowCount()); row++) {
            List<SeatCell> cells = new ArrayList<>();
            for (int column = 1; column <= columns; column++) {
                PlacedStudent placed = byPosition.get(row + ":" + column);
                boolean exists = (row - 1) * columns + column <= room.capacity();
                cells.add(new SeatCell(row, column, placed == null ? null : placed.student(), exists));
            }
            rows.add(List.copyOf(cells));
        }
        return new SeatingGrid(room, List.copyOf(rows), students.size());
    }

    /** The invigilators as one readable line. */
    public String invigilatorNames() {
        return invigilators.stream()
                .map(member -> member.getName() + " (" + member.getId() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("none assigned");
    }
}
