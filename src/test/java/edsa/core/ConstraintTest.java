package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Each rule, checked against a plan built by hand to break exactly one thing. */
class ConstraintTest {

    private static final SchedulingConfig CONFIG = SchedulingConfig.defaults();

    private static ExamSlot slot(String id, String code, int hour, int minutes) {
        return new ExamSlot(id, code, code + " paper", TestData.DAY, LocalTime.of(hour, 0), minutes);
    }

    private static ExamData data() {
        return new ExamData(
                TestData.students("S", 6, "CS301", "EC301"),
                List.of(Room.ofCapacity("R1", "Room 1", 2, "Block A"),
                        Room.ofCapacity("R2", "Room 2", 2, "Block A")),
                TestData.faculty(2),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(10, 0))));
    }

    private static ExamPlan plan(List<ExamSlot> schedule, SeatingPlan seating, DutyRoster duties) {
        return new ExamPlan(data(), ExamMode.MIDSEM, schedule, seating, duties);
    }

    @Test
    void capacityPassesWhenTheRoomIsExactlyFull() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E1", new Seat("R1", 1, 2, "S2"));

        assertTrue(new RoomCapacityConstraint(CONFIG)
                .isSatisfied(plan(List.of(slot("E1", "CS301", 9, 120)), seating, new DutyRoster())));
    }

    @Test
    void capacityNamesTheRoomThatIsOverFull() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E1", new Seat("R1", 1, 2, "S2"));
        seating.place("E1", new Seat("R1", 2, 1, "S3"));

        List<String> breaches = new RoomCapacityConstraint(CONFIG)
                .breaches(plan(List.of(slot("E1", "CS301", 9, 120)), seating, new DutyRoster()));

        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("Room R1 holds 2 but 3 students"), breaches.get(0));
    }

    @Test
    void roomOverlapCatchesOneRoomInTwoSittingsAtOnce() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E2", new Seat("R1", 1, 2, "S2"));

        List<String> breaches = new RoomOverlapConstraint().breaches(
                plan(List.of(slot("E1", "CS301", 9, 120), slot("E2", "EC301", 10, 60)), seating, new DutyRoster()));

        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("Room R1 is booked"), breaches.get(0));
    }

    @Test
    void roomOverlapAllowsAroomReusedAfterTheFirstSittingEnds() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E2", new Seat("R1", 1, 2, "S2"));

        assertTrue(new RoomOverlapConstraint().isSatisfied(
                plan(List.of(slot("E1", "CS301", 9, 60), slot("E2", "EC301", 10, 60)), seating, new DutyRoster())));
    }

    @Test
    void studentConflictCatchesOverlappingPapers() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E2", new Seat("R2", 1, 1, "S1"));

        List<String> breaches = new StudentConflictConstraint().breaches(
                plan(List.of(slot("E1", "CS301", 9, 120), slot("E2", "EC301", 10, 60)), seating, new DutyRoster()));

        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("Student S1 is scheduled"), breaches.get(0));
    }

    @Test
    void studentConflictCatchesTwoSeatsForOnePaper() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E1", new Seat("R2", 1, 1, "S1"));

        List<String> breaches = new StudentConflictConstraint().breaches(
                plan(List.of(slot("E1", "CS301", 9, 120)), seating, new DutyRoster()));

        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("seated more than once"), breaches.get(0));
    }

    @Test
    void facultyConflictCatchesOverlappingDuties() {
        DutyRoster duties = new DutyRoster();
        duties.assign(new Assignment("E1", "R1", "F1"));
        duties.assign(new Assignment("E2", "R2", "F1"));

        List<String> breaches = new FacultyConflictConstraint().breaches(
                plan(List.of(slot("E1", "CS301", 9, 120), slot("E2", "EC301", 10, 60)),
                        new SeatingPlan(), duties));

        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("Invigilator F1 is on duty"), breaches.get(0));
    }

    @Test
    void facultyConflictCatchesTwoRoomsInOneSitting() {
        DutyRoster duties = new DutyRoster();
        duties.assign(new Assignment("E1", "R1", "F1"));
        duties.assign(new Assignment("E1", "R2", "F1"));

        assertFalse(new FacultyConflictConstraint()
                .isSatisfied(plan(List.of(slot("E1", "CS301", 9, 120)), new SeatingPlan(), duties)));
    }

    @Test
    void durationRuleRejectsANonPositiveDuration() {
        List<String> breaches = new ExamDurationConstraint().breaches(
                plan(List.of(slot("E1", "CS301", 9, 0)), new SeatingPlan(), new DutyRoster()));

        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("duration of 0 minutes"), breaches.get(0));
    }

    @Test
    void theCheckerReportsEveryRuleWhetherItPassedOrNot() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E1", new Seat("R1", 1, 2, "S2"));
        seating.place("E1", new Seat("R1", 2, 1, "S3"));

        List<CheckResult> results = PlanChecker.using(CONFIG)
                .inspect(plan(List.of(slot("E1", "CS301", 9, 120)), seating, new DutyRoster()));

        assertEquals(5, results.size());
        assertEquals(1, results.stream().filter(result -> !result.satisfied()).count());
        assertEquals("Room capacity",
                results.stream().filter(result -> !result.satisfied()).findFirst().orElseThrow().constraintName());
    }
}
