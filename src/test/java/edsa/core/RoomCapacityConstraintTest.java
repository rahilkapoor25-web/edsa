package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class RoomCapacityConstraintTest {

    private static final Room SMALL_ROOM = new Room("R1", "Tutorial Room", 2, 2, "Block A");
    private static final ExamSlot MORNING =
            new ExamSlot("E1", "CS301", "Data Structures", LocalDate.of(2026, 11, 10),
                    LocalTime.of(9, 30), LocalTime.of(12, 30));

    private final RoomCapacityConstraint constraint = new RoomCapacityConstraint();

    private static ExamPlan planSeating(int students) {
        List<Student> cohort = IntStream.rangeClosed(1, students)
                .mapToObj(i -> new Student("S" + i, "Student " + i, "CSE", Set.of("CS301")))
                .toList();
        ExamData data = new ExamData(cohort, List.of(SMALL_ROOM), List.of(), List.of(MORNING));

        SeatingPlan seating = new SeatingPlan();
        for (int i = 0; i < students; i++) {
            seating.place(MORNING.getId(), new Seat(SMALL_ROOM.getId(), 1 + i / 2, 1 + i % 2, "S" + (i + 1)));
        }
        return new ExamPlan(data, seating, new DutyRoster());
    }

    @Test
    void acceptsARoomFilledToCapacity() {
        assertTrue(constraint.isSatisfied(planSeating(4)));
    }

    @Test
    void acceptsAnEmptyPlan() {
        assertTrue(constraint.isSatisfied(planSeating(0)));
    }

    @Test
    void rejectsOneStudentTooMany() {
        assertFalse(constraint.isSatisfied(planSeating(5)));
    }

    @Test
    void isHardSoCarriesNoPenalty() {
        assertTrue(constraint instanceof HardConstraint);
        assertEquals(0, constraint.penalty(planSeating(5)));
    }
}
