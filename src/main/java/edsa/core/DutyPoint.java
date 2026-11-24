package edsa.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One point won or lost on the duty roster: -1 to an invigilator for each duty a confirmed
 * absence makes them miss, +1 to the colleague who covers it.
 *
 * <p>Worked out from the plan as it stood before the repair, with the same "does the absence
 * cover this sitting" rule {@link AbsenceRepairer} uses, so the points always match the duties
 * that actually moved. A duty nobody could take still costs the absent invigilator their point.
 */
public record DutyPoint(String facultyId, String facultyName, int points, String reason) {

    public static List<DutyPoint> earnedBy(ExamPlan before, AbsenceWindow window, RepairResult repair) {
        List<DutyPoint> points = new ArrayList<>();
        Faculty absent = repair.absent();
        if (absent == null) {
            return points;
        }
        for (Assignment duty : before.duties().forFaculty(absent.getId())) {
            ExamSlot slot = before.slot(duty.slotId());
            if (slot != null && window.covers(slot)) {
                points.add(new DutyPoint(absent.getId(), absent.getName(), -1,
                        "Missed " + describe(slot, duty.roomId())));
            }
        }
        for (DutyChange change : repair.changes()) {
            points.add(new DutyPoint(change.to().getId(), change.to().getName(), 1,
                    "Covered " + describe(change.slot(), change.roomId()) + " for " + absent.getName()));
        }
        return points;
    }

    private static String describe(ExamSlot slot, String roomId) {
        return slot.getPaperCode() + " in " + roomId + " on " + slot.getDate() + " "
                + slot.getStartTime() + "–" + slot.getEndTime();
    }
}
