package edsa.core;

import java.util.ArrayList;
import java.util.List;

/** Nobody invigilates a paper of their own department — a Physics paper gets no Physics staff. */
public final class DepartmentConflictConstraint implements HardConstraint {

    @Override
    public String name() {
        return "Department conflict";
    }

    @Override
    public List<String> breaches(ExamPlan plan) {
        List<String> problems = new ArrayList<>();
        for (Assignment duty : plan.duties().all()) {
            ExamSlot slot = plan.slot(duty.slotId());
            Faculty member = plan.data().facultyMember(duty.facultyId());
            String department = slot == null ? null : plan.data().departmentOf(slot.getId());
            if (member != null && member.belongsTo(department)) {
                problems.add("Invigilator " + member.getId() + " (" + member.getDepartment() + ") is on duty for "
                        + slot.getPaperCode() + " (" + slot.getId() + "), a " + department + " paper");
            }
        }
        return problems;
    }
}
