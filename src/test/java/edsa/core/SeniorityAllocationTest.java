package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Sharing duties out by rank: who gets asked, who is passed over, and why.
 *
 * <p>The point being tested is not that senior staff get fewer duties — that is arithmetic — but
 * that asking less of them does not land the difference on one unlucky colleague, and that every
 * refusal to use somebody is a reason a coordinator could act on.
 */
class SeniorityAllocationTest {

    private static final SeniorityLadder LADDER = SeniorityLadder.defaults();

    private static List<Student> join(List<Student> first, List<Student> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private DutyEligibility eligibility(DutyPolicy policy) {
        return new DutyEligibility(LADDER, policy, LeaveCalendar.empty());
    }

    private DutyEligibility eligibility(DutyPolicy policy, LeaveCalendar leave) {
        return new DutyEligibility(LADDER, policy, leave);
    }

    /** Six papers across six rooms, so there is enough work to spread unevenly. */
    private ExamData sixSittings(List<Faculty> faculty) {
        return new ExamData(
                join(TestData.students("S", 6, "CS301"), TestData.students("T", 6, "EC301")),
                TestData.rooms(6, 2),
                faculty,
                // Afternoon for the second paper, so both are placed and there are six duties
                // to share rather than three: with everybody capped at one, nothing is shared.
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(14, 0))));
    }

    private int dutiesOf(ExamPlan plan, String facultyId) {
        return plan.duties().forFaculty(facultyId).size();
    }

    @Test
    void anExplicitSeniorityValueOverridesTheDesignation() {
        Faculty lecturerWhoOutranks = new Faculty("F1", "Faculty 1", "CSE", false, Set.of(),
                "LECTURER", 99);
        Faculty professor = new Faculty("F2", "Faculty 2", "CSE", false, Set.of(), "PROFESSOR", null);

        assertEquals(99, LADDER.seniorityOf(lecturerWhoOutranks),
                "the explicit value is the whole reason for supporting one");
        assertEquals(50, LADDER.seniorityOf(professor));
        assertTrue(LADDER.isSenior(lecturerWhoOutranks));
    }

    @Test
    void anUnrecognisedDesignationIsReportedRatherThanRejected() {
        List<Faculty> faculty = TestData.ranked("PROFESSOR", "VISITING_FELLOW");

        assertEquals(List.of("VISITING_FELLOW"), LADDER.unrecognisedIn(faculty));
        assertEquals(0, LADDER.seniorityOf(faculty.get(1)), "placed below every configured rank");
        assertEquals(1.0, LADDER.dutyShareOf(faculty.get(1)),
                "and carries a full share, so imported data that predates the ladder still works");
    }

    @Test
    void aWorkbookSeniorFlagStillCountsAsSenior() {
        Faculty flagged = new Faculty("F1", "Faculty 1", "CSE", true, Set.of());

        assertTrue(LADDER.isSenior(flagged), "data from before the ladder existed keeps working");
    }

    @Test
    void weightingAsksLessOfASeniorRankWithoutOverloadingAnybody() {
        ExamData data = sixSittings(TestData.ranked("PROFESSOR", "ASSISTANT_PROFESSOR_I",
                "ASSISTANT_PROFESSOR_I", "ASSISTANT_PROFESSOR_I"));
        ExamPlanner planner = new ExamPlanner(SchedulingConfig.defaults(),
                eligibility(DutyPolicy.defaults()));

        ExamPlan plan = planner.plan(data, ExamMode.MIDSEM).plan();

        int professorDuties = dutiesOf(plan, "F1");
        int mostJunior = Stream.of("F2", "F3", "F4").mapToInt(id -> dutiesOf(plan, id)).max().orElseThrow();
        int fewestJunior = Stream.of("F2", "F3", "F4").mapToInt(id -> dutiesOf(plan, id)).min().orElseThrow();

        assertTrue(professorDuties <= fewestJunior,
                "a half-share rank should not be the busiest: professor had " + professorDuties
                        + " and the quietest full-share colleague had " + fewestJunior);
        assertTrue(professorDuties < mostJunior,
                "with six duties to share the professor should carry strictly fewer than the busiest");
        assertTrue(mostJunior - fewestJunior <= 1,
                "the work asked of the full-share ranks should still be even, not dumped on one of them");
    }

    @Test
    void unweightedItSpreadsEvenlyAndIgnoresRankAltogether() {
        ExamData data = sixSittings(TestData.ranked("PROFESSOR", "LECTURER", "LECTURER", "LECTURER"));
        ExamPlanner planner = new ExamPlanner(SchedulingConfig.defaults(),
                eligibility(DutyPolicy.unweighted()));

        ExamPlan plan = planner.plan(data, ExamMode.MIDSEM).plan();

        int most = plan.data().faculty().stream().mapToInt(m -> dutiesOf(plan, m.getId())).max().orElseThrow();
        int fewest = plan.data().faculty().stream().mapToInt(m -> dutiesOf(plan, m.getId())).min().orElseThrow();
        assertTrue(most - fewest <= 1, "with weighting off, rank buys nothing: " + fewest + "–" + most);
    }

    @Test
    void aRankConfiguredToCarryNoDutiesIsNeverPutOnOne() {
        SeniorityLadder excusing = new SeniorityLadder(List.of(
                new Designation("DEAN", "Dean", 60, 0.0),
                new Designation("LECTURER", "Lecturer", 20, 1.0)), 50);
        DutyEligibility eligibility = new DutyEligibility(excusing, DutyPolicy.defaults(),
                LeaveCalendar.empty());
        ExamData data = sixSittings(List.of(
                new Faculty("F1", "The Dean", "CSE", false, Set.of(), "DEAN", null),
                new Faculty("F2", "Faculty 2", "CSE", false, Set.of(), "LECTURER", null),
                new Faculty("F3", "Faculty 3", "CSE", false, Set.of(), "LECTURER", null)));

        ExamPlan plan = new ExamPlanner(SchedulingConfig.defaults(), eligibility)
                .plan(data, ExamMode.MIDSEM).plan();

        assertEquals(0, dutiesOf(plan, "F1"), "a zero share means excused, not lightly loaded");
        assertTrue(dutiesOf(plan, "F2") + dutiesOf(plan, "F3") > 0);
    }

    @Test
    void aCourseTeacherIsKeptOffTheirOwnPaperAndSaysSo() {
        ExamData data = new ExamData(
                TestData.students("S", 2, "CS301"),
                TestData.rooms(1, 2),
                List.of(TestData.teaching("F1", "CS301"), TestData.teaching("F2")),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));
        DutyEligibility eligibility = eligibility(DutyPolicy.defaults());

        ExamPlan plan = new ExamPlanner(SchedulingConfig.defaults(), eligibility)
                .plan(data, ExamMode.MIDSEM).plan();

        assertEquals("F2", plan.duties().forSlot("E1").get(0).facultyId(),
                "the paper's own teacher should not be invigilating it");
        ExamSlot slot = plan.slot("E1");
        assertEquals("teaches CS301",
                eligibility.refusal(data.facultyMember("F1"), slot, List.of(), 0).orElseThrow());
    }

    @Test
    void aCourseTeacherMayInvigilateWhenTheOfficeAllowsIt() {
        DutyPolicy permissive = new DutyPolicy(true, 0, 0, true, false);
        DutyEligibility eligibility = eligibility(permissive);
        ExamData data = new ExamData(
                TestData.students("S", 2, "CS301"),
                TestData.rooms(1, 2),
                List.of(TestData.teaching("F1", "CS301")),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));

        ExamPlan plan = new ExamPlanner(SchedulingConfig.defaults(), eligibility)
                .plan(data, ExamMode.MIDSEM).plan();

        assertEquals("F1", plan.duties().forSlot("E1").get(0).facultyId(),
                "the rule is configuration, and some institutions ask exactly the opposite");
    }

    @Test
    void somebodyOnConfirmedLeaveIsNeverGivenADuty() {
        ExamData data = new ExamData(
                TestData.students("S", 2, "CS301"),
                TestData.rooms(1, 2),
                TestData.faculty(2),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));
        LeaveCalendar away = LeaveCalendar.empty()
                .add("F1", new AbsenceWindow(TestData.DAY.atTime(8, 0), TestData.DAY.atTime(13, 0)));

        ExamPlan plan = new ExamPlanner(SchedulingConfig.defaults(),
                eligibility(DutyPolicy.defaults(), away)).plan(data, ExamMode.MIDSEM).plan();

        assertEquals("F2", plan.duties().forSlot("E1").get(0).facultyId());
    }

    @Test
    void leaveThatEndsBeforeTheSittingStartsDoesNotExcuseAnybody() {
        ExamSlot slot = ExamSlot.between("E1", "CS301", "CS301 paper",
                TestData.DAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        // End-exclusive, like every other interval here: away until 09:00 means available at 09:00.
        LeaveCalendar morningOnly = LeaveCalendar.empty()
                .add("F1", new AbsenceWindow(TestData.DAY.atTime(7, 0), TestData.DAY.atTime(9, 0)));

        assertFalse(morningOnly.isAway("F1", slot));
    }

    @Test
    void theCapStopsTheAllocatorLeaningOnWhoeverSortsFirst() {
        DutyPolicy capped = new DutyPolicy(false, 1, 0, true, false);
        ExamData data = sixSittings(TestData.faculty(6));

        ExamPlan plan = new ExamPlanner(SchedulingConfig.defaults(), eligibility(capped))
                .plan(data, ExamMode.MIDSEM).plan();

        for (Faculty member : data.faculty()) {
            assertTrue(dutiesOf(plan, member.getId()) <= 1,
                    member.getId() + " was given more than the cap of 1");
        }
    }

    @Test
    void anUnstaffedRoomReportsWhyEachColleagueWasPassedOver() {
        ExamData data = new ExamData(
                TestData.students("S", 2, "CS301"),
                TestData.rooms(1, 2),
                List.of(TestData.teaching("F1", "CS301")),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));

        PlanningResult result = new ExamPlanner(SchedulingConfig.defaults(),
                eligibility(DutyPolicy.defaults())).plan(data, ExamMode.MIDSEM);

        String problem = result.allocationProblems().stream()
                .filter(text -> text.startsWith("No invigilator is free"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the unstaffed room was not reported: "
                        + result.allocationProblems()));
        assertTrue(problem.contains("F1 teaches CS301"),
                "the reason has to be actionable, not just 'nobody was free': " + problem);
    }

    @Test
    void theSamePlanIsProducedEveryTime() {
        ExamData data = sixSittings(TestData.ranked("PROFESSOR", "LECTURER", "LECTURER", "LECTURER"));
        ExamPlanner planner = new ExamPlanner(SchedulingConfig.defaults(),
                eligibility(DutyPolicy.defaults()));

        List<String> first = planner.plan(data, ExamMode.MIDSEM).plan().duties().all().stream()
                .map(Assignment::toString).sorted().toList();
        List<String> second = planner.plan(data, ExamMode.MIDSEM).plan().duties().all().stream()
                .map(Assignment::toString).sorted().toList();

        assertEquals(first, second, "weighting must not make the allocator non-deterministic");
    }

    /**
     * The case that nearly slipped through: at the first room nobody has any duties, so every
     * relative load is zero and the ordering falls entirely to the tie-break. Before the share
     * became the second key, a professor whose id sorted first was handed the first room — which
     * made the weighting worthless in exactly the small session where it is most visible.
     */
    @Test
    void aHalfShareRankIsAskedLastEvenWhenNobodyHasADutyYet() {
        ExamData data = new ExamData(
                TestData.students("S", 6, "CS301"),
                TestData.rooms(3, 2),
                TestData.ranked("PROFESSOR", "ASSISTANT_PROFESSOR_I", "ASSISTANT_PROFESSOR_I",
                        "ASSISTANT_PROFESSOR_I"),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));

        ExamPlan plan = new ExamPlanner(SchedulingConfig.defaults(),
                eligibility(DutyPolicy.defaults())).plan(data, ExamMode.MIDSEM).plan();

        assertEquals(0, dutiesOf(plan, "F1"),
                "three rooms and three full-share colleagues: the professor should not be used at all");
        assertEquals(3, Stream.of("F2", "F3", "F4").mapToInt(id -> dutiesOf(plan, id)).sum());
    }

    @Test
    void relativeLoadIsWhatMakesAWeightedLadderComparable() {
        DutyEligibility weighted = eligibility(DutyPolicy.defaults());
        Faculty professor = new Faculty("F1", "F1", "CSE", false, Set.of(), "PROFESSOR", null);
        Faculty assistant = new Faculty("F2", "F2", "CSE", false, Set.of(),
                "ASSISTANT_PROFESSOR_I", null);

        // One duty on a half share is as loaded as two on a full share.
        assertEquals(weighted.relativeLoad(professor, 1), weighted.relativeLoad(assistant, 2), 0.0001);
        assertTrue(weighted.relativeLoad(professor, 1) > weighted.relativeLoad(assistant, 1));
    }
}
