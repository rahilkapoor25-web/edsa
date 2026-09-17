package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The checker reports how many things break a rule, not just that something does. */
class BreachCountTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    private static final ExamSlot MORNING = new ExamSlot("E1", "CS301", "Data Structures", DAY,
            LocalTime.of(9, 0), LocalTime.of(12, 0));
    private static final ExamSlot OVERLAPPING = new ExamSlot("E2", "EC301", "Signals", DAY,
            LocalTime.of(10, 0), LocalTime.of(13, 0));

    private static final Room TINY_ONE = new Room("R1", "Tutorial One", 1, 1, "Block A");
    private static final Room TINY_TWO = new Room("R2", "Tutorial Two", 1, 1, "Block A");

    private static ExamData data() {
        List<Student> students = List.of(
                new Student("S1", "One", "CSE", Set.of("CS301")),
                new Student("S2", "Two", "CSE", Set.of("CS301")),
                new Student("S3", "Three", "CSE", Set.of("CS301")),
                new Student("S4", "Four", "CSE", Set.of("CS301")));
        List<Faculty> faculty = List.of(
                new Faculty("F1", "Alpha", "CSE", true, Set.of()),
                new Faculty("F2", "Beta", "CSE", false, Set.of()));
        return new ExamData(students, List.of(TINY_ONE, TINY_TWO), faculty, List.of(MORNING, OVERLAPPING));
    }

    @Test
    void countsEveryRoomThatIsOverFull() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E1", new Seat("R1", 1, 2, "S2"));
        seating.place("E1", new Seat("R2", 1, 1, "S3"));
        seating.place("E1", new Seat("R2", 1, 2, "S4"));
        ExamPlan plan = new ExamPlan(data(), seating, new DutyRoster());

        RoomCapacityConstraint constraint = new RoomCapacityConstraint();

        assertEquals(2, constraint.breaches(plan));
        assertFalse(constraint.isSatisfied(plan));
    }

    @Test
    void countsNothingWhenEveryRoomFits() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        ExamPlan plan = new ExamPlan(data(), seating, new DutyRoster());

        RoomCapacityConstraint constraint = new RoomCapacityConstraint();

        assertEquals(0, constraint.breaches(plan));
        assertTrue(constraint.isSatisfied(plan));
    }

    @Test
    void countsEachPersonCaughtInTwoPlacesAtOnce() {
        DutyRoster duties = new DutyRoster();
        duties.assign(new Assignment("E1", "R1", "F1"));
        duties.assign(new Assignment("E2", "R2", "F1"));
        duties.assign(new Assignment("E1", "R2", "F2"));
        duties.assign(new Assignment("E2", "R1", "F2"));
        ExamPlan plan = new ExamPlan(data(), new SeatingPlan(), duties);

        assertEquals(2, new DoubleBookingConstraint().breaches(plan));
    }

    @Test
    void reportsEveryRuleWhetherItPassedOrNot() {
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        seating.place("E1", new Seat("R1", 1, 2, "S2"));
        ExamPlan plan = new ExamPlan(data(), seating, new DutyRoster());

        List<CheckResult> results = PlanChecker.standard().inspect(plan);

        assertEquals(2, results.size());
        assertEquals("Room capacity", results.get(0).constraintName());
        assertFalse(results.get(0).satisfied());
        assertEquals(1, results.get(0).breaches());
        assertTrue(results.get(1).satisfied());
        assertEquals(0, results.get(1).breaches());
    }
}
