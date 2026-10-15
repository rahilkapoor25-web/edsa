package edsa.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Covers an invigilator who cannot attend, moving as few other people as possible.
 *
 * <p>Each of the absent member's duties is offered to the least-loaded colleague who is free
 * at that time, so one absence moves one person per duty and nobody else is disturbed. No
 * cascade is attempted: if nobody is free for a room, that is reported rather than fixed by
 * shuffling three other people, because the coordinator needs to know a room is uncovered.
 *
 * <p>Seating is untouched — students sit where they were told to sit.
 */
public final class AbsenceRepairer {

    /**
     * Replaces the absent member's duties.
     *
     * @param onlyOn the day they are away, or null when they are away for the whole session
     */
    public RepairResult repair(ExamPlan plan, String absentFacultyId, LocalDate onlyOn) {
        Faculty absent = plan.data().facultyMember(absentFacultyId);
        if (absent == null) {
            return new RepairResult(plan, null, List.of(),
                    List.of("There is no invigilator with id " + absentFacultyId));
        }

        Map<String, List<ExamSlot>> covers = coversBySlotHolder(plan);
        List<DutyChange> changes = new ArrayList<>();
        List<String> uncovered = new ArrayList<>();
        DutyRoster repaired = new DutyRoster();

        for (Assignment duty : plan.duties().all()) {
            ExamSlot slot = plan.slot(duty.slotId());
            boolean affected = duty.facultyId().equals(absentFacultyId)
                    && slot != null
                    && (onlyOn == null || slot.getDate().equals(onlyOn));
            if (!affected) {
                repaired.assign(duty);
                continue;
            }

            Faculty replacement = freeReplacement(plan, slot, absentFacultyId, covers);
            if (replacement == null) {
                uncovered.add("Nobody is free to take " + duty.roomId() + " for " + slot.getPaperCode()
                        + " on " + slot.getDate() + " at " + slot.getStartTime() + "-" + slot.getEndTime());
                continue;
            }
            repaired.assign(new Assignment(duty.slotId(), duty.roomId(), replacement.getId()));
            covers.computeIfAbsent(replacement.getId(), key -> new ArrayList<>()).add(slot);
            covers.getOrDefault(absentFacultyId, new ArrayList<>()).remove(slot);
            changes.add(new DutyChange(slot, duty.roomId(), absent, replacement));
        }

        ExamPlan newPlan = new ExamPlan(plan.data(), plan.mode(), plan.schedule(), plan.seating(), repaired);
        return new RepairResult(newPlan, absent, List.copyOf(changes), List.copyOf(uncovered));
    }

    /** The least-loaded colleague with no clashing duty, or null when there is none. */
    private Faculty freeReplacement(ExamPlan plan, ExamSlot slot, String absentFacultyId,
                                    Map<String, List<ExamSlot>> covers) {
        return plan.data().faculty().stream()
                .filter(candidate -> !candidate.getId().equals(absentFacultyId))
                .filter(candidate -> covers.getOrDefault(candidate.getId(), List.of()).stream()
                        .noneMatch(slot::overlaps))
                .min(Comparator.comparingInt((Faculty candidate) ->
                                covers.getOrDefault(candidate.getId(), List.of()).size())
                        .thenComparing(Faculty::getId))
                .orElse(null);
    }

    private Map<String, List<ExamSlot>> coversBySlotHolder(ExamPlan plan) {
        Map<String, List<ExamSlot>> covers = new HashMap<>();
        for (Assignment duty : plan.duties().all()) {
            ExamSlot slot = plan.slot(duty.slotId());
            if (slot != null) {
                covers.computeIfAbsent(duty.facultyId(), key -> new ArrayList<>()).add(slot);
            }
        }
        return covers;
    }
}
