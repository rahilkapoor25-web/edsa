package edsa.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Every sitting must run for the length its mode dictates, and finish on the day it started.
 *
 * <p>This is the independent guard on the duration pipeline. The scheduler asks
 * {@link ExamMode#minutesFor(Integer)} when it times a paper; this rule asks the same question
 * again afterwards and compares, so a sitting timed from a stale workbook value — a ten-minute
 * Quiz stretched to ninety — is reported as a broken rule rather than presented as a timetable.
 */
public final class ExamDurationConstraint implements HardConstraint {

    @Override
    public String name() {
        return "Exam duration";
    }

    @Override
    public List<String> breaches(ExamPlan plan) {
        List<String> problems = new ArrayList<>();
        for (ExamSlot slot : plan.schedule()) {
            if (slot.getDurationMinutes() <= 0) {
                problems.add(slot.getPaperCode() + " (" + slot.getId() + ") has a duration of "
                        + slot.getDurationMinutes() + " minutes");
                continue;
            }
            if (!slot.getEndTime().isAfter(slot.getStartTime())) {
                problems.add(slot.getPaperCode() + " (" + slot.getId() + ") starts at "
                        + slot.getStartTime() + " and runs past midnight");
                continue;
            }
            int required = requiredMinutes(plan, slot);
            if (slot.getDurationMinutes() != required) {
                problems.add(slot.getPaperCode() + " (" + slot.getId() + ") is timed for "
                        + slot.getDurationMinutes() + " minutes but a " + plan.mode().label()
                        + " paper must run for " + required + " minutes");
            }
        }
        return problems;
    }

    /** What the mode says this paper should run for, taking its declared length into account. */
    private int requiredMinutes(ExamPlan plan, ExamSlot slot) {
        Exam exam = plan.data().exam(slot.getId());
        Integer declared = exam == null ? null : exam.getDurationOverrideMinutes().orElse(null);
        return plan.mode().minutesFor(declared);
    }
}
