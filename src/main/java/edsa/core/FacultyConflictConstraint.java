package edsa.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** An invigilator watches one room at a time, and never two overlapping sittings. */
public final class FacultyConflictConstraint implements HardConstraint {

    @Override
    public String name() {
        return "Faculty conflict";
    }

    @Override
    public List<String> breaches(ExamPlan plan) {
        List<String> problems = new ArrayList<>();
        List<ExamSlot> schedule = plan.schedule();

        for (ExamSlot slot : schedule) {
            Set<String> seen = new HashSet<>();
            for (Assignment duty : plan.duties().forSlot(slot.getId())) {
                if (!seen.add(duty.facultyId())) {
                    problems.add("Invigilator " + duty.facultyId() + " is assigned to more than one room for "
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
                Set<String> shared = new HashSet<>(Conflicts.invigilators(plan, one));
                shared.retainAll(Conflicts.invigilators(plan, other));
                for (String facultyId : shared) {
                    problems.add("Invigilator " + facultyId + " is on duty for " + one.getPaperCode()
                            + " (" + one.getStartTime() + "-" + one.getEndTime() + ") and "
                            + other.getPaperCode() + " (" + other.getStartTime() + "-"
                            + other.getEndTime() + ") on " + one.getDate());
                }
            }
        }
        return problems;
    }
}
