package edsa.core;

import java.util.HashMap;
import java.util.Map;

/** No room may hold more students than it has seats. */
public final class RoomCapacityConstraint implements HardConstraint {

    @Override
    public String name() {
        return "Room capacity";
    }

    @Override
    public boolean isSatisfied(ExamPlan plan) {
        for (String slotId : plan.seating().slotIds()) {
            Map<String, Integer> seatedPerRoom = new HashMap<>();
            for (Seat seat : plan.seating().seatsFor(slotId)) {
                seatedPerRoom.merge(seat.roomId(), 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : seatedPerRoom.entrySet()) {
                if (entry.getValue() > plan.data().room(entry.getKey()).capacity()) {
                    return false;
                }
            }
        }
        return true;
    }
}
