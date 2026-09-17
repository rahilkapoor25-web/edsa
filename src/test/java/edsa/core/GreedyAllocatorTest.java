package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class GreedyAllocatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    private static final ExamSlot MORNING = new ExamSlot("E1", "CS301", "Data Structures", DAY,
            LocalTime.of(9, 0), LocalTime.of(12, 0));
    private static final ExamSlot OVERLAPPING_MORNING = new ExamSlot("E2", "EC301", "Signals", DAY,
            LocalTime.of(10, 0), LocalTime.of(13, 0));

    private final GreedyAllocator allocator = new GreedyAllocator();
    private final PlanChecker checker = PlanChecker.standard();

    private static List<Student> cohort(String prefix, String paperCode, int size) {
        return IntStream.rangeClosed(1, size)
                .mapToObj(i -> new Student(prefix + i, "Student " + prefix + i, "CSE", Set.of(paperCode)))
                .toList();
    }

    private static List<Faculty> faculty(int size) {
        return IntStream.rangeClosed(1, size)
                .mapToObj(i -> new Faculty("F" + i, "Faculty " + i, "CSE", i == 1, Set.of()))
                .toList();
    }

    @Test
    void seatsEveryStudentAndLeavesNoViolation() {
        ExamData data = new ExamData(
                cohort("S", "CS301", 10),
                List.of(new Room("R1", "Hall One", 3, 4, "Block A")),
                faculty(2),
                List.of(MORNING));

        ExamPlan plan = allocator.allocate(data);

        assertEquals(10, plan.seating().seatsFor("E1").size());
        assertEquals(List.of(), checker.check(plan));
    }

    @Test
    void fillsTheRoomGridRowByRow() {
        ExamData data = new ExamData(
                cohort("S", "CS301", 3),
                List.of(new Room("R1", "Hall One", 2, 2, "Block A")),
                faculty(1),
                List.of(MORNING));

        List<Seat> seats = allocator.allocate(data).seating().seatsFor("E1");

        assertEquals(new Seat("R1", 1, 1, "S1"), seats.get(0));
        assertEquals(new Seat("R1", 1, 2, "S2"), seats.get(1));
        assertEquals(new Seat("R1", 2, 1, "S3"), seats.get(2));
    }

    @Test
    void givesEveryUsedRoomAnInvigilator() {
        ExamData data = new ExamData(
                cohort("S", "CS301", 6),
                List.of(new Room("R1", "Hall One", 2, 2, "Block A"),
                        new Room("R2", "Hall Two", 2, 2, "Block A")),
                faculty(3),
                List.of(MORNING));

        ExamPlan plan = allocator.allocate(data);

        assertEquals(2, plan.duties().forSlot("E1").size());
        assertEquals(2, plan.duties().forSlot("E1").stream().map(Assignment::roomId).distinct().count());
        assertEquals(2, plan.duties().forSlot("E1").stream().map(Assignment::facultyId).distinct().count());
    }

    @Test
    void neverPutsOneInvigilatorInTwoOverlappingSlots() {
        ExamData data = new ExamData(
                concat(cohort("S", "CS301", 4), cohort("T", "EC301", 4)),
                List.of(new Room("R1", "Hall One", 2, 2, "Block A"),
                        new Room("R2", "Hall Two", 2, 2, "Block A")),
                faculty(2),
                List.of(MORNING, OVERLAPPING_MORNING));

        ExamPlan plan = allocator.allocate(data);

        assertEquals(List.of(), checker.check(plan));
        assertEquals(1, plan.duties().forFaculty("F1").size());
        assertEquals(1, plan.duties().forFaculty("F2").size());
    }

    @Test
    void spreadsDutiesAcrossTheLeastLoadedInvigilators() {
        ExamData data = new ExamData(
                concat(cohort("S", "CS301", 4), cohort("T", "EC301", 4)),
                List.of(new Room("R1", "Hall One", 2, 2, "Block A"),
                        new Room("R2", "Hall Two", 2, 2, "Block A")),
                faculty(4),
                List.of(MORNING, OVERLAPPING_MORNING));

        ExamPlan plan = allocator.allocate(data);

        assertTrue(plan.data().faculty().stream()
                .allMatch(member -> plan.duties().forFaculty(member.getId()).size() <= 1));
    }

    @Test
    void refusesToLoseStudentsWhenTheRoomsAreTooSmall() {
        ExamData data = new ExamData(
                cohort("S", "CS301", 10),
                List.of(new Room("R1", "Tutorial Room", 2, 2, "Block A")),
                faculty(1),
                List.of(MORNING));

        CapacityException thrown = assertThrows(CapacityException.class, () -> allocator.allocate(data));

        assertTrue(thrown.getMessage().contains("6 of 10 students"), thrown.getMessage());
    }

    @Test
    void refusesToLeaveARoomWithoutAnInvigilator() {
        ExamData data = new ExamData(
                concat(cohort("S", "CS301", 4), cohort("T", "EC301", 4)),
                List.of(new Room("R1", "Hall One", 2, 2, "Block A"),
                        new Room("R2", "Hall Two", 2, 2, "Block A")),
                faculty(1),
                List.of(MORNING, OVERLAPPING_MORNING));

        assertThrows(NoInvigilatorAvailableException.class, () -> allocator.allocate(data));
    }

    @Test
    void takesTheMostConstrainedSlotFirst() {
        ExamData data = new ExamData(
                concat(cohort("S", "CS301", 2), cohort("T", "EC301", 8)),
                List.of(new Room("R1", "Big Hall", 4, 3, "Block A"),
                        new Room("R2", "Small Hall", 2, 1, "Block A")),
                faculty(4),
                List.of(MORNING, OVERLAPPING_MORNING));

        ExamPlan plan = allocator.allocate(data);

        assertEquals("R1", plan.seating().seatsFor("E2").get(0).roomId(),
                "the larger cohort should have claimed the larger room first");
        assertEquals("R2", plan.seating().seatsFor("E1").get(0).roomId());
    }

    private static List<Student> concat(List<Student> first, List<Student> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
