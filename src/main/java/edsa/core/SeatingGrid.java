package edsa.core;

import java.util.List;

/**
 * One room's seating for one sitting, laid out as the grid it is.
 *
 * <p>Built from the allocation and nothing else: every occupied cell is a seat
 * {@code GreedyAllocator} assigned, at the row and column it assigned. The exporter and the
 * page both read this, so the printed sheet and the screen cannot disagree.
 *
 * <p>Shaped here rather than in a template because a page should not be looping over
 * coordinates to work out what goes where.
 */
public record SeatingGrid(Room room, List<List<SeatCell>> rows, int seated) {

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return rows.isEmpty() ? 0 : rows.get(0).size();
    }

    public int capacity() {
        return room.capacity();
    }

    /** Seats that exist and are empty — not counting positions the room does not seat. */
    public int free() {
        return (int) rows.stream().flatMap(List::stream).filter(SeatCell::isFree).count();
    }
}
