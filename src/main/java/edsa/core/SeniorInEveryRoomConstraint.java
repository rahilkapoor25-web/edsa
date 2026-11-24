package edsa.core;

import java.util.ArrayList;
import java.util.List;

/** A junior never invigilates a room alone: every staffed room has a Professor in it. */
public final class SeniorInEveryRoomConstraint implements HardConstraint {

    @Override
    public String name() {
        return "Senior in every room";
    }

    @Override
    public List<String> breaches(ExamPlan plan) {
        List<String> problems = new ArrayList<>();
        for (ExamSlot slot : plan.schedule()) {
            for (String roomId : Conflicts.rooms(plan, slot)) {
                List<String> staff = plan.duties().forSlot(slot.getId()).stream()
                        .filter(duty -> duty.roomId().equals(roomId))
                        .map(Assignment::facultyId)
                        .toList();
                boolean hasSenior = staff.stream()
                        .map(id -> plan.data().facultyMember(id))
                        .anyMatch(member -> member != null && member.isSenior());
                if (!staff.isEmpty() && !hasSenior) {
                    problems.add("Room " + roomId + " for " + slot.getPaperCode() + " (" + slot.getId()
                            + ") has no Professor: " + String.join(" and ", staff) + " are junior");
                }
            }
        }
        return problems;
    }
}
