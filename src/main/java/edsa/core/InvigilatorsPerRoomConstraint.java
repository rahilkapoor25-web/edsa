package edsa.core;

import java.util.ArrayList;
import java.util.List;

/** Every room in use has exactly {@link SchedulingConfig#INVIGILATORS_PER_ROOM} invigilators. */
public final class InvigilatorsPerRoomConstraint implements HardConstraint {

    @Override
    public String name() {
        return "Invigilators per room";
    }

    @Override
    public List<String> breaches(ExamPlan plan) {
        List<String> problems = new ArrayList<>();
        int wanted = SchedulingConfig.INVIGILATORS_PER_ROOM;
        for (ExamSlot slot : plan.schedule()) {
            for (String roomId : Conflicts.rooms(plan, slot)) {
                long staff = plan.duties().forSlot(slot.getId()).stream()
                        .filter(duty -> duty.roomId().equals(roomId)).count();
                if (staff != wanted) {
                    problems.add("Room " + roomId + " for " + slot.getPaperCode() + " (" + slot.getId() + ") has "
                            + staff + (staff == 1 ? " invigilator" : " invigilators") + "; every room needs " + wanted);
                }
            }
        }
        return problems;
    }
}
