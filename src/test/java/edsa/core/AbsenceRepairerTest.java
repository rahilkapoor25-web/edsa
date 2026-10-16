package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Covering an absence: who picks the rooms up, and what happens when nobody can. */
class AbsenceRepairerTest {

    private static final AbsenceWindow WHOLE_SESSION = AbsenceWindow.wholeSession();

    private final ExamPlanner planner = new ExamPlanner();
    private final AbsenceRepairer repairer = new AbsenceRepairer();

    private static List<Student> join(List<Student> first, List<Student> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    /** Two papers at once in two rooms, with a spare invigilator sitting idle. */
    private ExamPlan twoParallelPapers(int facultyCount) {
        ExamData data = new ExamData(
                join(TestData.students("S", 10, "CS301"), TestData.students("T", 10, "EC301")),
                TestData.rooms(2, 10),
                TestData.faculty(facultyCount),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(9, 0))));
        return planner.plan(data, ExamMode.MIDSEM).plan();
    }

    private static String invigilatorOf(ExamPlan plan, String slotId) {
        return plan.duties().forSlot(slotId).get(0).facultyId();
    }

    /** The sittings somebody holds, sorted, so an assertion never assumes an allocation order. */
    private static List<String> slotsOf(ExamPlan plan, String facultyId) {
        return plan.duties().forFaculty(facultyId).stream().map(Assignment::slotId).sorted().toList();
    }

    private static List<String> without(List<String> slots, String removed) {
        return slots.stream().filter(slot -> !slot.equals(removed)).toList();
    }

    @Test
    void theAbsentInvigilatorIsReplacedBySomebodyFree() {
        ExamPlan plan = twoParallelPapers(3);
        String away = invigilatorOf(plan, "E1");

        RepairResult repair = repairer.repair(plan, away, WHOLE_SESSION);

        assertTrue(repair.isComplete(), String.valueOf(repair.uncovered()));
        assertEquals(1, repair.changes().size());
        assertEquals(away, repair.changes().get(0).from().getId());
        assertFalse(repair.changes().get(0).to().getId().equals(away));
        assertTrue(repair.plan().duties().forFaculty(away).isEmpty(), "they keep no duties");
    }

    @Test
    void theRepairedPlanStillSatisfiesEveryRule() {
        ExamPlan plan = twoParallelPapers(3);

        RepairResult repair = repairer.repair(plan, invigilatorOf(plan, "E1"), WHOLE_SESSION);

        assertEquals(List.of(), PlanChecker.standard().check(repair.plan()));
    }

    @Test
    void everyRoomStillHasAnInvigilator() {
        ExamPlan plan = twoParallelPapers(3);
        int dutiesBefore = plan.duties().all().size();

        RepairResult repair = repairer.repair(plan, invigilatorOf(plan, "E1"), WHOLE_SESSION);

        assertEquals(dutiesBefore, repair.plan().duties().all().size());
    }

    @Test
    void nobodyElseIsDisturbed() {
        ExamPlan plan = twoParallelPapers(3);
        String away = invigilatorOf(plan, "E1");
        String untouched = invigilatorOf(plan, "E2");

        RepairResult repair = repairer.repair(plan, away, WHOLE_SESSION);

        assertEquals(untouched, invigilatorOf(repair.plan(), "E2"),
                "the other room's invigilator should not move");
        assertEquals(1, repair.changes().size(), "one absence moves one person per room");
    }

    @Test
    void seatingIsNeverTouched() {
        ExamPlan plan = twoParallelPapers(3);

        RepairResult repair = repairer.repair(plan, invigilatorOf(plan, "E1"), WHOLE_SESSION);

        assertEquals(plan.seating().seatsFor("E1"), repair.plan().seating().seatsFor("E1"));
        assertEquals(plan.seating().seatedCount(), repair.plan().seating().seatedCount());
    }

    @Test
    void anUncoverableRoomIsReportedRatherThanHidden() {
        ExamPlan plan = twoParallelPapers(2);

        RepairResult repair = repairer.repair(plan, invigilatorOf(plan, "E1"), WHOLE_SESSION);

        assertFalse(repair.isComplete());
        assertTrue(repair.uncovered().get(0).contains("Nobody is free to take"), repair.uncovered().toString());
    }

    @Test
    void onlyTheDayTheyAreAwayIsChanged() {
        ExamData data = new ExamData(
                TestData.students("S", 8, "CS301", "CS302"),
                TestData.rooms(1, 10),
                TestData.faculty(2),
                List.of(new Exam("E1", "CS301", "Day one paper", TestData.DAY, LocalTime.of(9, 0), null),
                        new Exam("E2", "CS302", "Day two paper", TestData.DAY.plusDays(1),
                                LocalTime.of(9, 0), null)));
        ExamPlan plan = planner.plan(data, ExamMode.MIDSEM).plan();
        String away = invigilatorOf(plan, "E1");

        RepairResult repair = repairer.repair(plan, away, AbsenceWindow.wholeDay(TestData.DAY));

        assertEquals(1, repair.changes().size());
        assertEquals(TestData.DAY, repair.changes().get(0).slot().getDate());
        assertTrue(repair.plan().duties().forFaculty(away).stream()
                        .allMatch(duty -> duty.slotId().equals("E2")),
                "their duties on other days are left alone");
    }

    @Test
    void anUnknownInvigilatorIsReportedNotIgnored() {
        ExamPlan plan = twoParallelPapers(3);

        RepairResult repair = repairer.repair(plan, "NOBODY", WHOLE_SESSION);

        assertTrue(repair.uncovered().get(0).contains("no invigilator with id NOBODY"));
        assertEquals(0, repair.changes().size());
    }

    @Test
    void someoneWithNoDutiesChangesNothing() {
        ExamPlan plan = twoParallelPapers(3);
        String idle = plan.workloads().stream()
                .filter(workload -> workload.duties() == 0)
                .findFirst().orElseThrow().member().getId();

        RepairResult repair = repairer.repair(plan, idle, WHOLE_SESSION);

        assertTrue(repair.isComplete());
        assertFalse(repair.changedAnything());
    }

    // ── Absences measured as intervals, not as whole days ────────────────────────────────

    /** One professor, one room, three papers on one day: 09:00, 12:00 and 15:00. */
    private ExamPlan threeSittingsInOneDay() {
        ExamData data = new ExamData(
                List.of(new Student("S1", "Student One", "CSE", Set.of("CS301")),
                        new Student("S2", "Student Two", "CSE", Set.of("CS302")),
                        new Student("S3", "Student Three", "CSE", Set.of("CS303"))),
                TestData.rooms(1, 10),
                TestData.faculty(2),
                List.of(new Exam("E1", "CS301", "Morning", TestData.DAY, LocalTime.of(9, 0), null),
                        new Exam("E2", "CS302", "Midday", TestData.DAY, LocalTime.of(12, 0), null),
                        new Exam("E3", "CS303", "Afternoon", TestData.DAY, LocalTime.of(15, 0), null)));
        return planner.plan(data, ExamMode.MIDSEM).plan();
    }

    @Test
    void aMorningsLeaveTakesOnlyTheMorningsDuties() {
        ExamPlan plan = threeSittingsInOneDay();
        String away = invigilatorOf(plan, "E1");
        List<String> heldBefore = slotsOf(plan, away);

        RepairResult repair = repairer.repair(plan, away,
                AbsenceWindow.hours(TestData.DAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        assertEquals(List.of("E1"), repair.changes().stream()
                        .map(change -> change.slot().getId()).toList(),
                "only the 09:00-11:00 paper overlaps the absence");
        assertEquals(without(heldBefore, "E1"), slotsOf(repair.plan(), away),
                "every other duty of theirs is left exactly where it was");
    }

    @Test
    void theEndOfAnAbsenceIsExclusive() {
        ExamPlan plan = threeSittingsInOneDay();
        String away = invigilatorOf(plan, "E2");

        RepairResult repair = repairer.repair(plan, away,
                AbsenceWindow.hours(TestData.DAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        assertTrue(repair.changes().stream().noneMatch(change -> change.slot().getId().equals("E2")),
                "a paper starting exactly when the absence ends is not affected");
    }

    @Test
    void theStartOfAnAbsenceIsInclusive() {
        ExamPlan plan = threeSittingsInOneDay();
        String away = invigilatorOf(plan, "E2");

        RepairResult repair = repairer.repair(plan, away,
                AbsenceWindow.hours(TestData.DAY, LocalTime.of(12, 0), LocalTime.of(13, 0)));

        assertEquals(1, repair.changes().size(), "a paper starting exactly when the absence starts is affected");
        assertEquals("E2", repair.changes().get(0).slot().getId());
    }

    @Test
    void anAbsenceEndingPartWayThroughASittingStillTakesIt() {
        ExamPlan plan = threeSittingsInOneDay();
        String away = invigilatorOf(plan, "E3");

        RepairResult repair = repairer.repair(plan, away,
                AbsenceWindow.hours(TestData.DAY, LocalTime.of(14, 0), LocalTime.of(15, 30)));

        assertEquals(1, repair.changes().size(), "they cannot sit the first half hour of it");
        assertEquals("E3", repair.changes().get(0).slot().getId());
    }

    @Test
    void anAbsenceThatMissesEverySittingChangesNothing() {
        ExamPlan plan = threeSittingsInOneDay();
        String away = invigilatorOf(plan, "E1");

        RepairResult repair = repairer.repair(plan, away,
                AbsenceWindow.hours(TestData.DAY, LocalTime.of(17, 30), LocalTime.of(18, 0)));

        assertTrue(repair.isComplete());
        assertFalse(repair.changedAnything());
        assertEquals(slotsOf(plan, away), slotsOf(repair.plan(), away), "they keep every duty");
    }

    @Test
    void aMultiDayAbsenceTakesEveryDayItCovers() {
        ExamData data = new ExamData(
                TestData.students("S", 8, "CS301", "CS302", "CS303"),
                TestData.rooms(1, 10),
                TestData.faculty(2),
                List.of(new Exam("E1", "CS301", "Day one", TestData.DAY, LocalTime.of(9, 0), null),
                        new Exam("E2", "CS302", "Day two", TestData.DAY.plusDays(1), LocalTime.of(9, 0), null),
                        new Exam("E3", "CS303", "Day four", TestData.DAY.plusDays(3), LocalTime.of(9, 0), null)));
        ExamPlan plan = planner.plan(data, ExamMode.MIDSEM).plan();
        String away = invigilatorOf(plan, "E1");

        List<String> heldBefore = slotsOf(plan, away);
        AbsenceWindow twoDays = AbsenceWindow.days(TestData.DAY, TestData.DAY.plusDays(1));

        RepairResult repair = repairer.repair(plan, away, twoDays);

        List<String> expectedToMove = heldBefore.stream()
                .filter(slotId -> twoDays.covers(plan.slot(slotId)))
                .toList();
        List<String> expectedToStay = heldBefore.stream()
                .filter(slotId -> !twoDays.covers(plan.slot(slotId)))
                .toList();

        assertFalse(expectedToMove.isEmpty(), "the scenario is pointless unless something moves");
        assertFalse(expectedToStay.isEmpty(), "and unless something outside the absence stays");
        assertEquals(expectedToMove, repair.changes().stream()
                .map(change -> change.slot().getId()).sorted().toList());
        assertEquals(expectedToStay, slotsOf(repair.plan(), away),
                "the paper outside the absence stays with them");
    }

    @Test
    void aColleagueAlreadyAwayIsNotOfferedTheRoom() {
        ExamPlan plan = twoParallelPapers(3);
        String away = invigilatorOf(plan, "E1");
        String alsoAway = plan.data().faculty().stream()
                .map(Faculty::getId)
                .filter(id -> !id.equals(away) && !id.equals(invigilatorOf(plan, "E2")))
                .findFirst().orElseThrow();

        RepairResult repair = repairer.repair(plan, away, WHOLE_SESSION,
                List.of(new Absence(alsoAway, WHOLE_SESSION)));

        assertFalse(repair.isComplete(), "the only free colleague is away too, so the room is reported");
        assertTrue(repair.uncovered().get(0).contains("Nobody is free to take"), repair.uncovered().toString());
    }

    @Test
    void theOriginalPlanIsNeverModified() {
        ExamPlan plan = twoParallelPapers(3);
        String away = invigilatorOf(plan, "E1");
        int dutiesBefore = plan.duties().forFaculty(away).size();

        RepairResult repair = repairer.repair(plan, away, WHOLE_SESSION);

        assertEquals(dutiesBefore, plan.duties().forFaculty(away).size(),
                "the plan handed in keeps its roster, so a caller either adopts the repair or keeps the original");
        assertTrue(repair.plan().duties().forFaculty(away).isEmpty());
    }

    @Test
    void anAbsenceMustEndAfterItStarts() {
        assertThrows(IllegalArgumentException.class, () ->
                AbsenceWindow.hours(TestData.DAY, LocalTime.of(12, 0), LocalTime.of(9, 0)));
        assertThrows(IllegalArgumentException.class, () ->
                AbsenceWindow.hours(TestData.DAY, LocalTime.of(9, 0), LocalTime.of(9, 0)));
    }
}
