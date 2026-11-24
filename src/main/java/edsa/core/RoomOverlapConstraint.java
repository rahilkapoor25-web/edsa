package edsa.core;

import java.util.ArrayList;
import java.util.List;

/** A room cannot host two sittings whose times overlap, or that leave it no time to turn round. */
public final class RoomOverlapConstraint implements HardConstraint {

    private final SchedulingConfig config;

    public RoomOverlapConstraint(SchedulingConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "Room overlap";
    }

    @Override
    public List<String> breaches(ExamPlan plan) {
        List<String> problems = new ArrayList<>();
        List<ExamSlot> schedule = plan.schedule();
        for (int i = 0; i < schedule.size(); i++) {
            for (int j = i + 1; j < schedule.size(); j++) {
                ExamSlot one = schedule.get(i);
                ExamSlot other = schedule.get(j);
                if (!one.overlaps(other, config.turnaroundMinutes())) {
                    continue;
                }
                String tooClose = one.overlaps(other) ? ""
                        : " without the " + config.turnaroundMinutes() + "-minute turnaround between them";
                for (String roomId : Conflicts.rooms(plan, one)) {
                    if (Conflicts.rooms(plan, other).contains(roomId)) {
                        problems.add("Room " + roomId + " is booked for " + one.getPaperCode()
                                + " (" + one.getStartTime() + "-" + one.getEndTime() + ") and "
                                + other.getPaperCode() + " (" + other.getStartTime() + "-"
                                + other.getEndTime() + ") on " + one.getDate() + tooClose);
                    }
                }
            }
        }
        return problems;
    }
}
