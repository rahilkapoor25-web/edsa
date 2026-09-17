package edsa.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DoubleBookingConstraintTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    private static final ExamSlot MORNING = new ExamSlot("E1", "CS301", "Data Structures", DAY,
            LocalTime.of(9, 0), LocalTime.of(12, 0));
    private static final ExamSlot OVERLAPPING_MORNING = new ExamSlot("E2", "EC301", "Signals", DAY,
            LocalTime.of(10, 0), LocalTime.of(13, 0));
    private static final ExamSlot AFTERNOON = new ExamSlot("E3", "ME301", "Thermodynamics", DAY,
            LocalTime.of(14, 0), LocalTime.of(17, 0));

    private static final Room HALL_ONE = new Room("R1", "Hall One", 5, 5, "Block A");
    private static final Room HALL_TWO = new Room("R2", "Hall Two", 5, 5, "Block A");

    private final DoubleBookingConstraint constraint = new DoubleBookingConstraint();

    private static ExamPlan plan(SeatingPlan seating, DutyRoster duties) {
        Student student = new Student("S1", "Aarav Sharma", "CSE", Set.of("CS301", "EC301", "ME301"));
        Faculty member = new Faculty("F1", "Dr. Meera Nair", "CSE", true, Set.of("CS301"));
        ExamData data = new ExamData(List.of(student), List.of(HALL_ONE, HALL_TWO), List.of(member),
                List.of(MORNING, OVERLAPPING_MORNING, AFTERNOON));
        return new ExamPlan(data, seating, duties);
    }

    @Test
    void acceptsAnInvigilatorWorkingSlotsThatDoNotOverlap() {
        DutyRoster duties = new DutyRoster();
        duties.assign(new Assignment(MORNING.getId(), HALL_ONE.getId(), "F1"));
        duties.assign(new Assignment(AFTERNOON.getId(), HALL_ONE.getId(), "F1"));

        assertTrue(constraint.isSatisfied(plan(new SeatingPlan(), duties)));
    }

    @Test
    void rejectsAnInvigilatorInTwoOverlappingSlots() {
        DutyRoster duties = new DutyRoster();
        duties.assign(new Assignment(MORNING.getId(), HALL_ONE.getId(), "F1"));
        duties.assign(new Assignment(OVERLAPPING_MORNING.getId(), HALL_TWO.getId(), "F1"));

        assertFalse(constraint.isSatisfied(plan(new SeatingPlan(), duties)));
    }

    @Test
    void rejectsAnInvigilatorInTwoRoomsInTheSameSlot() {
        DutyRoster duties = new DutyRoster();
        duties.assign(new Assignment(MORNING.getId(), HALL_ONE.getId(), "F1"));
        duties.assign(new Assignment(MORNING.getId(), HALL_TWO.getId(), "F1"));

        assertFalse(constraint.isSatisfied(plan(new SeatingPlan(), duties)));
    }

    @Test
    void rejectsOneRoomHostingTwoOverlappingSlots() {
        SeatingPlan seating = new SeatingPlan();
        seating.place(MORNING.getId(), new Seat(HALL_ONE.getId(), 1, 1, "S1"));
        seating.place(OVERLAPPING_MORNING.getId(), new Seat(HALL_ONE.getId(), 2, 2, "S2"));

        assertFalse(constraint.isSatisfied(plan(seating, new DutyRoster())));
    }

    @Test
    void acceptsOneRoomReusedAfterTheMorningPaperIsOver() {
        SeatingPlan seating = new SeatingPlan();
        seating.place(MORNING.getId(), new Seat(HALL_ONE.getId(), 1, 1, "S1"));
        seating.place(AFTERNOON.getId(), new Seat(HALL_ONE.getId(), 1, 1, "S1"));

        assertTrue(constraint.isSatisfied(plan(seating, new DutyRoster())));
    }

    @Test
    void rejectsAStudentSittingTwoOverlappingPapers() {
        SeatingPlan seating = new SeatingPlan();
        seating.place(MORNING.getId(), new Seat(HALL_ONE.getId(), 1, 1, "S1"));
        seating.place(OVERLAPPING_MORNING.getId(), new Seat(HALL_TWO.getId(), 1, 1, "S1"));

        assertFalse(constraint.isSatisfied(plan(seating, new DutyRoster())));
    }

    @Test
    void rejectsAStudentSeatedTwiceInOneRoom() {
        SeatingPlan seating = new SeatingPlan();
        seating.place(MORNING.getId(), new Seat(HALL_ONE.getId(), 1, 1, "S1"));
        seating.place(MORNING.getId(), new Seat(HALL_ONE.getId(), 3, 4, "S1"));

        assertFalse(constraint.isSatisfied(plan(seating, new DutyRoster())));
    }

    @Test
    void acceptsAnEmptyPlan() {
        assertTrue(constraint.isSatisfied(plan(new SeatingPlan(), new DutyRoster())));
    }
}
