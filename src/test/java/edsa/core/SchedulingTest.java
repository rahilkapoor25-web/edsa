package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Scheduling, seating and staffing a session, and the rules that keep it honest. */
class SchedulingTest {

    private final ExamPlanner planner = new ExamPlanner();

    private static List<Student> join(List<Student> first, List<Student> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    @Test
    void twoPapersRunInParallelInDifferentRoomsWhenNobodyClashes() {
        ExamData data = new ExamData(
                join(TestData.students("S", 10, "CS301"), TestData.students("T", 10, "EC301")),
                TestData.rooms(2, 10),
                TestData.faculty(2),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        assertEquals(2, result.plan().schedule().size());
        assertEquals(LocalTime.of(9, 0), result.plan().schedule().get(0).getStartTime());
        assertEquals(LocalTime.of(9, 0), result.plan().schedule().get(1).getStartTime());
        assertEquals(2, result.plan().roomsUsed().size());
    }

    @Test
    void aStudentSittingBothPapersPushesTheSecondLater() {
        List<Student> shared = TestData.students("S", 6, "CS301", "EC301");
        ExamData data = new ExamData(shared, TestData.rooms(2, 10), TestData.faculty(2),
                List.of(TestData.exam("E1", "CS301", null), TestData.exam("E2", "EC301", null)));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        List<ExamSlot> schedule = result.plan().schedule();
        assertFalse(schedule.get(0).overlaps(schedule.get(1)));
        assertEquals(LocalTime.of(11, 0), schedule.get(1).getStartTime());
    }

    @Test
    void aLargeCohortIsSplitAcrossEnoughRooms() {
        ExamData data = new ExamData(TestData.students("S", 75, "CS301"), TestData.rooms(4, 25),
                TestData.faculty(4), List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.ENDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        assertEquals(75, result.plan().seating().seatedCount());
        assertEquals(3, result.plan().roomsUsed().size());
        assertEquals(3, result.plan().duties().all().size());
    }

    @Test
    void noRoomEverHoldsMoreThanItsCapacity() {
        ExamData data = new ExamData(TestData.students("S", 48, "CS301"), TestData.rooms(3, 20),
                TestData.faculty(3), List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.ENDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        result.plan().sittings().forEach(sitting ->
                assertTrue(sitting.seated() <= sitting.room().capacity(),
                        sitting.room().getId() + " holds " + sitting.seated()));
    }

    @Test
    void noInvigilatorCoversTwoOverlappingSittings() {
        ExamData data = new ExamData(
                join(TestData.students("S", 8, "CS301"), TestData.students("T", 8, "EC301")),
                TestData.rooms(2, 10),
                TestData.faculty(2),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        Set<String> onDuty = new HashSet<>();
        result.plan().duties().all().forEach(duty -> assertTrue(onDuty.add(duty.facultyId()),
                duty.facultyId() + " is on duty twice at once"));
    }

    @Test
    void noRoomHostsTwoOverlappingSittings() {
        ExamData data = new ExamData(
                join(TestData.students("S", 8, "CS301"), TestData.students("T", 8, "EC301")),
                TestData.rooms(2, 10),
                TestData.faculty(2),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        List<RoomSitting> sittings = result.plan().sittings();
        for (int i = 0; i < sittings.size(); i++) {
            for (int j = i + 1; j < sittings.size(); j++) {
                if (sittings.get(i).room().getId().equals(sittings.get(j).room().getId())) {
                    assertFalse(sittings.get(i).slot().overlaps(sittings.get(j).slot()),
                            "one room hosts two overlapping sittings");
                }
            }
        }
    }

    @Test
    void aSecondPaperMayStartTheMomentTheFirstEnds() {
        List<Student> shared = TestData.students("S", 5, "CS301", "EC301");
        ExamData data = new ExamData(shared, TestData.rooms(1, 10), TestData.faculty(1),
                List.of(TestData.exam("E1", "CS301", null), TestData.exam("E2", "EC301", null)));

        PlanningResult result = planner.plan(data, ExamMode.QUIZ);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        assertEquals(LocalTime.of(9, 0), result.plan().schedule().get(0).getStartTime());
        assertEquals(LocalTime.of(9, 10), result.plan().schedule().get(1).getStartTime());
    }

    @Test
    void reportsWhyACohortTooBigForTheRoomsCannotBeSeated() {
        ExamData data = new ExamData(TestData.students("S", 40, "CS301"), TestData.rooms(1, 10),
                TestData.faculty(2), List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.ENDSEM);

        assertFalse(result.isUsable());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("cannot seat 40 students")),
                String.valueOf(result.problems()));
    }

    @Test
    void reportsAPaperNobodyIsRegisteredFor() {
        ExamData data = new ExamData(TestData.students("S", 5, "CS301"), TestData.rooms(1, 10),
                TestData.faculty(1), List.of(TestData.exam("E9", "ME999", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.QUIZ);

        assertFalse(result.isUsable());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("No student is registered")),
                String.valueOf(result.problems()));
    }

    @Test
    void reportsASittingThatWouldRunPastClosingTime() {
        ExamData data = new ExamData(TestData.students("S", 5, "CS301"), TestData.rooms(1, 10),
                TestData.faculty(1), List.of(TestData.exam("E1", "CS301", LocalTime.of(17, 0))));

        PlanningResult result = planner.plan(data, ExamMode.ENDSEM);

        assertFalse(result.isUsable());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("after the hall closes")),
                String.valueOf(result.problems()));
    }

    @Test
    void refusesToRunMoreRoomsAtOnceThanThereAreInvigilators() {
        ExamData data = new ExamData(
                join(TestData.students("S", 10, "CS301"), TestData.students("T", 10, "EC301")),
                TestData.rooms(2, 10),
                TestData.faculty(1),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        assertFalse(result.isUsable());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("invigilators")),
                String.valueOf(result.problems()));
    }

    @Test
    void everyStudentIsSeatedExactlyOncePerPaper() {
        ExamData data = new ExamData(TestData.students("S", 30, "CS301"), TestData.rooms(2, 20),
                TestData.faculty(2), List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        List<String> seated = new ArrayList<>(
                result.plan().seating().seatsFor("E1").stream().map(Seat::studentId).toList());
        assertEquals(30, seated.size());
        assertEquals(30, new HashSet<>(seated).size());
    }

    @Test
    void thePlanIsTheSameOnEveryRun() {
        ExamData data = new ExamData(
                join(TestData.students("S", 20, "CS301"), TestData.students("T", 14, "EC301")),
                TestData.rooms(3, 15),
                TestData.faculty(4),
                List.of(TestData.exam("E1", "CS301", null), TestData.exam("E2", "EC301", null)));

        PlanningResult first = planner.plan(data, ExamMode.MIDSEM);
        PlanningResult second = planner.plan(data, ExamMode.MIDSEM);

        assertEquals(first.plan().schedule().toString(), second.plan().schedule().toString());
        assertEquals(first.plan().duties().all(), second.plan().duties().all());
        first.plan().schedule().forEach(slot -> assertEquals(
                first.plan().seating().seatsFor(slot.getId()), second.plan().seating().seatsFor(slot.getId())));
    }

    @Test
    void handlesTwentyRoomsRunningAtOnce() {
        List<Student> everyone = new ArrayList<>();
        List<Exam> exams = new ArrayList<>();
        for (int paper = 1; paper <= 8; paper++) {
            String code = "CS30" + paper;
            everyone.addAll(TestData.students("P" + paper + "S", 25, code));
            exams.add(TestData.exam("E" + paper, code, null));
        }
        ExamData data = new ExamData(everyone, TestData.rooms(20, 25), TestData.faculty(20), exams);

        PlanningResult result = planner.plan(data, ExamMode.MIDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        assertEquals(8, result.plan().schedule().size());
        assertEquals(200, result.plan().seating().seatedCount());
    }
}
