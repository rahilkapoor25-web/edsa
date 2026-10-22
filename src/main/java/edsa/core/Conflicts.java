package edsa.core;

import java.util.List;

/** Who and what a sitting occupies, shared by the rules that look for clashes. */
final class Conflicts {

    private Conflicts() {
    }

    static List<String> rooms(ExamPlan plan, ExamSlot slot) {
        return plan.seating().seatsFor(slot.getId()).stream().map(Seat::roomId).distinct().toList();
    }

    static List<String> students(ExamPlan plan, ExamSlot slot) {
        return plan.seating().seatsFor(slot.getId()).stream().map(Seat::studentId).toList();
    }

    static List<String> invigilators(ExamPlan plan, ExamSlot slot) {
        return plan.duties().forSlot(slot.getId()).stream().map(Assignment::facultyId).toList();
    }
}
