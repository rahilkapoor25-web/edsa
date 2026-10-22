package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Looking one sitting up by its paper and its room.
 *
 * <p>This is what the room detail page asks for. The case worth pinning down is the one that
 * looks plausible but is not in the plan — a real paper and a real room that never actually
 * met — because answering that with somebody else's seating would be worse than answering
 * with nothing.
 */
class SittingLookupTest {

    /** Two papers at once in two rooms, so each paper uses one room and not the other. */
    private ExamPlan twoPapersInTwoRooms() {
        List<Student> students = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            students.add(new Student("A" + index, "Student A" + index, "CSE", Set.of("CS301")));
            students.add(new Student("B" + index, "Student B" + index, "ECE", Set.of("EC301")));
        }
        ExamData data = new ExamData(
                students,
                TestData.rooms(2, 10),
                TestData.faculty(4),
                List.of(new Exam("E1", "CS301", "Data Structures", TestData.DAY, LocalTime.of(9, 0), null),
                        new Exam("E2", "EC301", "Signals", TestData.DAY, LocalTime.of(9, 0), null)));
        return new ExamPlanner().plan(data, ExamMode.MIDSEM).plan();
    }

    @Test
    void aPaperAndTheRoomItIsSatInIsFound() {
        ExamPlan plan = twoPapersInTwoRooms();
        RoomSitting any = plan.sittings().get(0);

        var found = plan.sitting(any.slot().getId(), any.room().getId());

        assertTrue(found.isPresent());
        assertEquals(any.slot().getId(), found.get().slot().getId());
        assertEquals(any.room().getId(), found.get().room().getId());
        assertEquals(any.seated(), found.get().seated());
    }

    @Test
    void aRealPaperAndARealRoomThatNeverMetIsNotFound() {
        ExamPlan plan = twoPapersInTwoRooms();
        RoomSitting first = plan.sittings().get(0);
        String roomUsedByTheOtherPaper = plan.sittings().stream()
                .filter(sitting -> !sitting.slot().getId().equals(first.slot().getId()))
                .map(sitting -> sitting.room().getId())
                .findFirst().orElseThrow();

        assertFalse(roomUsedByTheOtherPaper.equals(first.room().getId()),
                "the two papers should be in different rooms");
        assertTrue(plan.sitting(first.slot().getId(), roomUsedByTheOtherPaper).isEmpty(),
                "that paper is not sat in that room, so there is no seating to show");
    }

    @Test
    void nonsenseIdentifiersAreNotFound() {
        ExamPlan plan = twoPapersInTwoRooms();

        assertTrue(plan.sitting("NO-SUCH-EXAM", "NO-SUCH-ROOM").isEmpty());
        assertTrue(plan.sitting(null, "R1").isEmpty());
        assertTrue(plan.sitting("E1", null).isEmpty());
    }

    @Test
    void theGridHoldsEverySeatTheAllocatorAssignedAndNoOthers() {
        ExamPlan plan = twoPapersInTwoRooms();
        RoomSitting sitting = plan.sittings().get(0);

        SeatingGrid grid = sitting.grid();

        assertEquals(sitting.room().getRowCount(), grid.rowCount());
        assertEquals(sitting.room().getColumnCount(), grid.columnCount());
        assertEquals(sitting.seated(), grid.seated());

        long taken = grid.rows().stream().flatMap(List::stream).filter(SeatCell::isTaken).count();
        assertEquals(sitting.seated(), taken, "one occupied cell per seated student");

        for (PlacedStudent placed : sitting.students()) {
            SeatCell cell = grid.rows().get(placed.row() - 1).get(placed.column() - 1);
            assertTrue(cell.isTaken());
            assertEquals(placed.student().getId(), cell.student().getId(),
                    "the student must be at the position the allocator gave them");
            assertEquals(placed.row() + "-" + placed.column(), cell.position());
        }
    }

    @Test
    void aPositionTheRoomDoesNotSeatIsMarkedApartFromAFreeOne() {
        ExamData data = new ExamData(
                List.of(new Student("S1", "Student One", "CSE", Set.of("CS301"))),
                List.of(Room.ofCapacity("R20", "Small Hall", 20, "Block A")),
                List.of(new Faculty("F1", "Faculty One", "CSE", false, Set.of())),
                List.of(new Exam("E1", "CS301", "Data Structures", TestData.DAY,
                        LocalTime.of(9, 0), null)));
        SeatingGrid grid = new ExamPlanner().plan(data, ExamMode.MIDSEM).plan()
                .sittings().get(0).grid();

        long free = grid.rows().stream().flatMap(List::stream).filter(SeatCell::isFree).count();
        long notSeats = grid.rows().stream().flatMap(List::stream)
                .filter(cell -> !cell.isTaken() && !cell.isFree()).count();

        // Six to a row over four rows is 24 positions; the room seats 20, one is taken.
        assertEquals(19, free);
        assertEquals(4, notSeats);
        assertEquals(19, grid.free());
    }
}
