package edsa.core;

import java.util.ArrayList;
import java.util.List;

/** Every sitting must last a positive number of minutes and finish on the day it started. */
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
            } else if (!slot.getEndTime().isAfter(slot.getStartTime())) {
                problems.add(slot.getPaperCode() + " (" + slot.getId() + ") starts at "
                        + slot.getStartTime() + " and runs past midnight");
            }
        }
        return problems;
    }
}
