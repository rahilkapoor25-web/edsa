package edsa.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Covers an invigilator who cannot attend, moving as few other people as possible.
 *
 * <p>Only the duties that fall inside the absence are touched. A professor away for one
 * morning loses that morning's rooms and keeps the afternoon's, because the absence and the
 * sitting are compared as intervals rather than by date — see
 * {@link AbsenceWindow#covers(ExamSlot)}.
 *
 * <p>Each affected duty is offered to the least-loaded colleague who is free at that time, so
 * one absence moves one person per duty and nobody else is disturbed. A colleague is free only
 * when they have no clashing duty and are not themselves away. No cascade is attempted: if
 * nobody is free for a room, that is reported rather than fixed by shuffling three other
 * people, because the coordinator needs to know a room is uncovered.
 *
 * <p>The repair is all-or-nothing by construction. A fresh {@link DutyRoster} is built and
 * returned inside a new plan; the plan handed in is never modified, so a caller either adopts
 * the whole repaired roster or keeps the whole original one. There is no state in which some
 * duties moved and others silently did not.
 *
 * <p>Seating is untouched — students sit where they were told to sit.
 */
public final class AbsenceRepairer {

    /**
     * Replaces the duties the absence covers.
     *
     * @param window    when they are away
     * @param othersAway everyone else already known to be away, so a room is never handed to
     *                   a colleague who cannot take it either
     */
    public RepairResult repair(ExamPlan plan, String absentFacultyId, AbsenceWindow window,
                               Collection<Absence> othersAway) {
        Faculty absent = plan.data().facultyMember(absentFacultyId);
        if (absent == null) {
            return new RepairResult(plan, null, List.of(),
                    List.of("There is no invigilator with id " + absentFacultyId));
        }

        List<Absence> unavailable = new ArrayList<>(othersAway);
        unavailable.removeIf(other -> other.facultyId().equals(absentFacultyId));

        Map<String, List<ExamSlot>> covers = coversBySlotHolder(plan);
        List<DutyChange> changes = new ArrayList<>();
        List<String> uncovered = new ArrayList<>();
        DutyRoster repaired = new DutyRoster();

        for (Assignment duty : plan.duties().all()) {
            ExamSlot slot = plan.slot(duty.slotId());
            boolean affected = duty.facultyId().equals(absentFacultyId)
                    && slot != null
                    && window.covers(slot);
            if (!affected) {
                repaired.assign(duty);
                continue;
            }

            Faculty replacement = freeReplacement(plan, slot, absentFacultyId, covers, unavailable);
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

    /** The same, when nobody else is known to be away. */
    public RepairResult repair(ExamPlan plan, String absentFacultyId, AbsenceWindow window) {
        return repair(plan, absentFacultyId, window, List.of());
    }

    /** The least-loaded colleague with no clashing duty and no absence of their own. */
    private Faculty freeReplacement(ExamPlan plan, ExamSlot slot, String absentFacultyId,
                                    Map<String, List<ExamSlot>> covers, List<Absence> unavailable) {
        return plan.data().faculty().stream()
                .filter(candidate -> !candidate.getId().equals(absentFacultyId))
                .filter(candidate -> unavailable.stream()
                        .noneMatch(away -> away.rulesOut(candidate.getId(), slot)))
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
