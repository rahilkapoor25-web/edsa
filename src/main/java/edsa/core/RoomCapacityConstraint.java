package edsa.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** No room may hold more students than it has seats. */
public final class RoomCapacityConstraint implements HardConstraint {

    private final SchedulingConfig config;

    public RoomCapacityConstraint(SchedulingConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "Room capacity";
    }

    @Override
    public List<String> breaches(ExamPlan plan) {
        List<String> problems = new ArrayList<>();
        for (ExamSlot slot : plan.schedule()) {
            Map<String, Integer> seatedPerRoom = new LinkedHashMap<>();
            for (Seat seat : plan.seating().seatsFor(slot.getId())) {
                seatedPerRoom.merge(seat.roomId(), 1, Integer::sum);
            }
            seatedPerRoom.forEach((roomId, seated) -> {
                int capacity = plan.data().capacityOf(roomId, config);
                if (seated > capacity) {
                    problems.add("Room " + roomId + " holds " + capacity + " but " + seated
                            + " students are seated there for " + slot.getPaperCode()
                            + " (" + slot.getId() + ")");
                }
            });
        }
        return problems;
    }
}
