package edsa.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nobody and no room can be in two places at once: not twice within one slot, and not in two
 * slots whose times overlap.
 */
public final class DoubleBookingConstraint implements HardConstraint {

    @Override
    public String name() {
        return "Double booking";
    }

    @Override
    public boolean isSatisfied(ExamPlan plan) {
        List<ExamSlot> slots = plan.data().slots();
        for (ExamSlot slot : slots) {
            if (hasRepeat(invigilators(plan, slot)) || hasRepeat(seatedStudents(plan, slot))) {
                return false;
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                ExamSlot one = slots.get(i);
                ExamSlot other = slots.get(j);
                if (!one.overlaps(other)) {
                    continue;
                }
                if (share(invigilators(plan, one), invigilators(plan, other))
                        || share(seatedStudents(plan, one), seatedStudents(plan, other))
                        || share(rooms(plan, one), rooms(plan, other))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<String> invigilators(ExamPlan plan, ExamSlot slot) {
        return plan.duties().forSlot(slot.getId()).stream().map(Assignment::facultyId).toList();
    }

    private static List<String> seatedStudents(ExamPlan plan, ExamSlot slot) {
        return plan.seating().seatsFor(slot.getId()).stream().map(Seat::studentId).toList();
    }

    private static List<String> rooms(ExamPlan plan, ExamSlot slot) {
        return plan.seating().seatsFor(slot.getId()).stream().map(Seat::roomId).distinct().toList();
    }

    private static boolean hasRepeat(List<String> ids) {
        return new HashSet<>(ids).size() != ids.size();
    }

    private static boolean share(List<String> ids, List<String> others) {
        Set<String> seen = new HashSet<>(ids);
        return !Collections.disjoint(seen, others);
    }
}
