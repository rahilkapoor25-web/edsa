package edsa.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A student sits one paper at a time, in one room: never two overlapping sittings, and never
 * two seats in the same sitting.
 */
public final class StudentConflictConstraint implements HardConstraint {

    @Override
    public String name() {
        return "Student conflict";
    }

    @Override
    public List<String> breaches(ExamPlan plan) {
        List<String> problems = new ArrayList<>();
        List<ExamSlot> schedule = plan.schedule();

        for (ExamSlot slot : schedule) {
            Set<String> seen = new HashSet<>();
            for (Seat seat : plan.seating().seatsFor(slot.getId())) {
                if (!seen.add(seat.studentId())) {
                    problems.add("Student " + seat.studentId() + " is seated more than once for "
                            + slot.getPaperCode() + " (" + slot.getId() + ")");
                }
            }
        }
        for (int i = 0; i < schedule.size(); i++) {
            for (int j = i + 1; j < schedule.size(); j++) {
                ExamSlot one = schedule.get(i);
                ExamSlot other = schedule.get(j);
                if (!one.overlaps(other)) {
                    continue;
                }
                Set<String> shared = new HashSet<>(Conflicts.students(plan, one));
                shared.retainAll(Conflicts.students(plan, other));
                for (String studentId : shared) {
                    problems.add("Student " + studentId + " is scheduled for " + one.getPaperCode()
                            + " (" + one.getStartTime() + "-" + one.getEndTime() + ") and "
                            + other.getPaperCode() + " (" + other.getStartTime() + "-"
                            + other.getEndTime() + ") on " + one.getDate());
                }
            }
        }
        return problems;
    }
}
