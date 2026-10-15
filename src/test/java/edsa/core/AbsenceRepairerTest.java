package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Covering an absence: who picks the rooms up, and what happens when nobody can. */
class AbsenceRepairerTest {

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

    @Test
    void theAbsentInvigilatorIsReplacedBySomebodyFree() {
        ExamPlan plan = twoParallelPapers(3);
        String away = invigilatorOf(plan, "E1");

        RepairResult repair = repairer.repair(plan, away, null);

        assertTrue(repair.isComplete(), String.valueOf(repair.uncovered()));
        assertEquals(1, repair.changes().size());
        assertEquals(away, repair.changes().get(0).from().getId());
        assertFalse(repair.changes().get(0).to().getId().equals(away));
        assertTrue(repair.plan().duties().forFaculty(away).isEmpty(), "they keep no duties");
    }

    @Test
    void theRepairedPlanStillSatisfiesEveryRule() {
        ExamPlan plan = twoParallelPapers(3);

        RepairResult repair = repairer.repair(plan, invigilatorOf(plan, "E1"), null);

        assertEquals(List.of(), PlanChecker.standard().check(repair.plan()));
    }

    @Test
    void everyRoomStillHasAnInvigilator() {
        ExamPlan plan = twoParallelPapers(3);
        int dutiesBefore = plan.duties().all().size();

        RepairResult repair = repairer.repair(plan, invigilatorOf(plan, "E1"), null);

        assertEquals(dutiesBefore, repair.plan().duties().all().size());
    }

    @Test
    void nobodyElseIsDisturbed() {
        ExamPlan plan = twoParallelPapers(3);
        String away = invigilatorOf(plan, "E1");
        String untouched = invigilatorOf(plan, "E2");

        RepairResult repair = repairer.repair(plan, away, null);

        assertEquals(untouched, invigilatorOf(repair.plan(), "E2"),
                "the other room's invigilator should not move");
        assertEquals(1, repair.changes().size(), "one absence moves one person per room");
    }

    @Test
    void seatingIsNeverTouched() {
        ExamPlan plan = twoParallelPapers(3);

        RepairResult repair = repairer.repair(plan, invigilatorOf(plan, "E1"), null);

        assertEquals(plan.seating().seatsFor("E1"), repair.plan().seating().seatsFor("E1"));
        assertEquals(plan.seating().seatedCount(), repair.plan().seating().seatedCount());
    }

    @Test
    void anUncoverableRoomIsReportedRatherThanHidden() {
        ExamPlan plan = twoParallelPapers(2);

        RepairResult repair = repairer.repair(plan, invigilatorOf(plan, "E1"), null);

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

        RepairResult repair = repairer.repair(plan, away, TestData.DAY);

        assertEquals(1, repair.changes().size());
        assertEquals(TestData.DAY, repair.changes().get(0).slot().getDate());
        assertTrue(repair.plan().duties().forFaculty(away).stream()
                        .allMatch(duty -> duty.slotId().equals("E2")),
                "their duties on other days are left alone");
    }

    @Test
    void anUnknownInvigilatorIsReportedNotIgnored() {
        ExamPlan plan = twoParallelPapers(3);

        RepairResult repair = repairer.repair(plan, "NOBODY", null);

        assertTrue(repair.uncovered().get(0).contains("no invigilator with id NOBODY"));
        assertEquals(0, repair.changes().size());
    }

    @Test
    void someoneWithNoDutiesChangesNothing() {
        ExamPlan plan = twoParallelPapers(3);
        String idle = plan.workloads().stream()
                .filter(workload -> workload.duties() == 0)
                .findFirst().orElseThrow().member().getId();

        RepairResult repair = repairer.repair(plan, idle, null);

        assertTrue(repair.isComplete());
        assertFalse(repair.changedAnything());
    }
}
