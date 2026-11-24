package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Two invigilators in every room, at least one of them a Professor, and never anybody from the
 * paper's own department — as the planner builds it, as the checker proves it, and as an
 * absence is covered.
 */
class StaffingRulesTest {

    private final ExamPlanner planner = new ExamPlanner();
    private static final SchedulingConfig CONFIG = SchedulingConfig.defaults();

    private static Set<String> staffOf(ExamPlan plan, String slotId) {
        return plan.duties().forSlot(slotId).stream().map(Assignment::facultyId).collect(Collectors.toSet());
    }

    // ── The planner ───────────────────────────────────────────────────────────────────────

    @Test
    void everyRoomGetsTwoInvigilatorsAndOneOfThemIsSenior() {
        ExamData data = new ExamData(TestData.students("S", 10, "PH101"), TestData.rooms(1, 30),
                List.of(TestData.member("F1", "CSE", Post.ASSISTANT_PROFESSOR),
                        TestData.member("F2", "CSE", Post.ASSOCIATE_PROFESSOR),
                        TestData.member("F3", "CSE", Post.PROFESSOR)),
                List.of(TestData.paper("E1", "PH101", LocalTime.of(9, 0), "PHY")));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        Set<String> staff = staffOf(result.plan(), "E1");
        assertEquals(2, staff.size());
        assertTrue(staff.contains("F3"), "the only Professor must be in the room: " + staff);
    }

    @Test
    void nobodyFromThePapersDepartmentInvigilatesIt() {
        ExamData data = new ExamData(TestData.students("S", 10, "PH101"), TestData.rooms(1, 30),
                List.of(TestData.member("F1", "PHY", Post.PROFESSOR),
                        TestData.member("F2", "PHY", Post.ASSISTANT_PROFESSOR),
                        TestData.member("F3", "CSE", Post.PROFESSOR),
                        TestData.member("F4", "CSE", Post.ASSISTANT_PROFESSOR)),
                List.of(TestData.paper("E1", "PH101", LocalTime.of(9, 0), "PHY")));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        assertEquals(Set.of("F3", "F4"), staffOf(result.plan(), "E1"));
    }

    @Test
    void aSeniorAndAJuniorArePairedBeforeTwoSeniors() {
        ExamData data = new ExamData(TestData.students("S", 10, "PH101"), TestData.rooms(1, 30),
                List.of(TestData.member("F1", "CSE", Post.PROFESSOR),
                        TestData.member("F2", "CSE", Post.PROFESSOR),
                        TestData.member("F3", "CSE", Post.ASSISTANT_PROFESSOR)),
                List.of(TestData.paper("E1", "PH101", LocalTime.of(9, 0), "PHY")));

        assertEquals(Set.of("F1", "F3"), staffOf(planner.plan(data, ExamMode.MIDSEM).plan(), "E1"));
    }

    @Test
    void aPaperIsNotRunWhenNoSeniorOutsideItsDepartmentIsFree() {
        ExamData data = new ExamData(TestData.students("S", 10, "PH101"), TestData.rooms(1, 30),
                List.of(TestData.member("F1", "PHY", Post.PROFESSOR),
                        TestData.member("F2", "CSE", Post.ASSISTANT_PROFESSOR),
                        TestData.member("F3", "CSE", Post.ASSOCIATE_PROFESSOR)),
                List.of(TestData.paper("E1", "PH101", LocalTime.of(9, 0), "PHY")));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        assertFalse(result.isUsable());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("Professor")
                && problem.contains("PHY")), String.valueOf(result.problems()));
    }

    @Test
    void twoRoomsAtOnceNeedTwoSeniors() {
        // Two papers at 09:00 with one Professor between them: the second cannot run then.
        ExamData data = new ExamData(
                Stream.concat(TestData.students("S", 10, "CS301").stream(),
                        TestData.students("T", 10, "EC301").stream()).toList(),
                TestData.rooms(2, 30),
                List.of(TestData.member("F1", "STAFF", Post.PROFESSOR),
                        TestData.member("F2", "STAFF", Post.ASSISTANT_PROFESSOR),
                        TestData.member("F3", "STAFF", Post.ASSISTANT_PROFESSOR),
                        TestData.member("F4", "STAFF", Post.ASSISTANT_PROFESSOR)),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        assertFalse(result.isUsable());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("Professor")),
                String.valueOf(result.problems()));
    }

    // ── The checker ───────────────────────────────────────────────────────────────────────

    private static ExamPlan handBuilt(List<Faculty> staff, List<Assignment> duties) {
        ExamData data = new ExamData(TestData.students("S", 2, "PH101"),
                List.of(Room.ofCapacity("R1", "Room 1", 10, "A")), staff,
                List.of(TestData.paper("E1", "PH101", LocalTime.of(9, 0), "PHY")));
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E1", new Seat("R1", 1, 3, "S2"));
        DutyRoster roster = new DutyRoster();
        duties.forEach(roster::assign);
        ExamSlot slot = new ExamSlot("E1", "PH101", "PH101 paper", TestData.DAY, LocalTime.of(9, 0), 120);
        return new ExamPlan(data, ExamMode.MIDSEM, List.of(slot), seating, roster);
    }

    private static List<String> breachesOf(String rule, ExamPlan plan) {
        return PlanChecker.using(CONFIG).inspect(plan).stream()
                .filter(result -> result.constraintName().equals(rule))
                .findFirst().orElseThrow().breaches();
    }

    @Test
    void theCheckerCatchesARoomWithOnlyOneInvigilator() {
        ExamPlan plan = handBuilt(List.of(TestData.member("F1", "CSE", Post.PROFESSOR)),
                List.of(new Assignment("E1", "R1", "F1")));

        List<String> breaches = breachesOf("Invigilators per room", plan);

        assertEquals(1, breaches.size(), String.valueOf(breaches));
        assertTrue(breaches.get(0).contains("R1"), breaches.get(0));
    }

    @Test
    void theCheckerCatchesTwoJuniorsAlone() {
        ExamPlan plan = handBuilt(List.of(TestData.member("F1", "CSE", Post.ASSISTANT_PROFESSOR),
                        TestData.member("F2", "CSE", Post.ASSOCIATE_PROFESSOR)),
                List.of(new Assignment("E1", "R1", "F1"), new Assignment("E1", "R1", "F2")));

        List<String> breaches = breachesOf("Senior in every room", plan);

        assertEquals(1, breaches.size(), String.valueOf(breaches));
        assertTrue(breaches.get(0).contains("no Professor"), breaches.get(0));
    }

    @Test
    void theCheckerCatchesAnInvigilatorFromThePapersDepartment() {
        ExamPlan plan = handBuilt(List.of(TestData.member("F1", "PHY", Post.PROFESSOR),
                        TestData.member("F2", "CSE", Post.ASSISTANT_PROFESSOR)),
                List.of(new Assignment("E1", "R1", "F1"), new Assignment("E1", "R1", "F2")));

        List<String> breaches = breachesOf("Department conflict", plan);

        assertEquals(1, breaches.size(), String.valueOf(breaches));
        assertTrue(breaches.get(0).contains("F1") && breaches.get(0).contains("PHY"), breaches.get(0));
    }

    @Test
    void aProperlyStaffedRoomPassesEveryStaffingRule() {
        ExamPlan plan = handBuilt(List.of(TestData.member("F1", "CSE", Post.PROFESSOR),
                        TestData.member("F2", "CSE", Post.ASSISTANT_PROFESSOR)),
                List.of(new Assignment("E1", "R1", "F1"), new Assignment("E1", "R1", "F2")));

        assertEquals(List.of(), breachesOf("Invigilators per room", plan));
        assertEquals(List.of(), breachesOf("Senior in every room", plan));
        assertEquals(List.of(), breachesOf("Department conflict", plan));
    }

    // ── Covering an absence ───────────────────────────────────────────────────────────────

    /** One PHY paper at 09:00, staffed by F1 (Professor) and F2 (junior); spares F3–F6. */
    private ExamPlan coveredPlan() {
        ExamData data = new ExamData(TestData.students("S", 10, "PH101"), TestData.rooms(1, 30),
                List.of(TestData.member("F1", "CSE", Post.PROFESSOR),
                        TestData.member("F2", "CSE", Post.ASSISTANT_PROFESSOR),
                        TestData.member("F3", "CSE", Post.ASSISTANT_PROFESSOR),
                        TestData.member("F4", "PHY", Post.PROFESSOR),
                        TestData.member("F5", "CSE", Post.PROFESSOR),
                        TestData.member("F6", "CSE", Post.ASSOCIATE_PROFESSOR)),
                List.of(TestData.paper("E1", "PH101", LocalTime.of(9, 0), "PHY")));
        ExamPlan plan = planner.plan(data, ExamMode.MIDSEM).plan();
        assertEquals(Set.of("F1", "F2"), staffOf(plan, "E1"), "test premise");
        return plan;
    }

    @Test
    void anAbsentSeniorIsReplacedByAnotherSeniorFromOutsideTheDepartment() {
        RepairResult repair = new AbsenceRepairer().repair(coveredPlan(), "F1", AbsenceWindow.wholeSession());

        assertTrue(repair.isComplete(), String.valueOf(repair.uncovered()));
        assertEquals("F5", repair.changes().get(0).to().getId(), "F4 is a Professor but from PHY");
    }

    @Test
    void anAbsentJuniorIsReplacedByAJuniorWhenTheRoomKeepsItsSenior() {
        RepairResult repair = new AbsenceRepairer().repair(coveredPlan(), "F2", AbsenceWindow.wholeSession());

        assertTrue(repair.isComplete(), String.valueOf(repair.uncovered()));
        assertEquals("F3", repair.changes().get(0).to().getId());
        assertTrue(PlanChecker.using(CONFIG).isLegal(repair.plan()));
    }
}
