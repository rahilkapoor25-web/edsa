package edsa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Where every student sits, slot by slot. */
public final class SeatingPlan {

    private final Map<String, List<Seat>> seatsBySlot = new LinkedHashMap<>();

    public void place(String slotId, Seat seat) {
        seatsBySlot.computeIfAbsent(slotId, key -> new ArrayList<>()).add(seat);
    }

    public List<Seat> seatsFor(String slotId) {
        return Collections.unmodifiableList(seatsBySlot.getOrDefault(slotId, List.of()));
    }

    public Set<String> slotIds() {
        return Collections.unmodifiableSet(seatsBySlot.keySet());
    }

    public int seatedCount() {
        return seatsBySlot.values().stream().mapToInt(List::size).sum();
    }
}
