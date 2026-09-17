package edsa.core;

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

    /** One breach per person or room caught in two places at once. */
    @Override
    public int breaches(ExamPlan plan) {
        List<ExamSlot> slots = plan.data().slots();
        int clashes = 0;

        for (ExamSlot slot : slots) {
            clashes += repeats(invigilators(plan, slot)) + repeats(seatedStudents(plan, slot));
        }
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                ExamSlot one = slots.get(i);
                ExamSlot other = slots.get(j);
                if (!one.overlaps(other)) {
                    continue;
                }
                clashes += shared(invigilators(plan, one), invigilators(plan, other))
                        + shared(seatedStudents(plan, one), seatedStudents(plan, other))
                        + shared(rooms(plan, one), rooms(plan, other));
            }
        }
        return clashes;
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

    private static int repeats(List<String> ids) {
        return ids.size() - new HashSet<>(ids).size();
    }

    private static int shared(List<String> ids, List<String> others) {
        Set<String> overlap = new HashSet<>(ids);
        overlap.retainAll(new HashSet<>(others));
        return overlap.size();
    }
}
