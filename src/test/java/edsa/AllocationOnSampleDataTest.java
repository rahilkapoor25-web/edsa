package edsa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.Assignment;
import edsa.core.ExamData;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;
import edsa.core.Faculty;
import edsa.core.GreedyAllocator;
import edsa.core.PlanChecker;
import edsa.core.Seat;
import edsa.core.Student;
import edsa.data.CsvReader;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The whole engine, end to end, on the four sample files that ship with the application. */
class AllocationOnSampleDataTest {

    private static ExamData data;

    private final PlanChecker checker = PlanChecker.standard();
    private final GreedyAllocator allocator = new GreedyAllocator();

    @BeforeAll
    static void loadSampleFiles() throws URISyntaxException {
        CsvReader reader = new CsvReader();
        data = new ExamData(
                reader.readStudents(sample("students.csv")),
                reader.readRooms(sample("rooms.csv")),
                reader.readFaculty(sample("faculty.csv")),
                reader.readSlots(sample("timetable.csv")));
    }

    private static Path sample(String name) throws URISyntaxException {
        return Path.of(AllocationOnSampleDataTest.class.getResource("/sample/" + name).toURI());
    }

    @Test
    void producesAPlanWithNoViolations() {
        assertEquals(List.of(), checker.check(allocator.allocate(data)));
    }

    @Test
    void seatsEveryStudentForEveryPaperTheySit() {
        ExamPlan plan = allocator.allocate(data);

        for (ExamSlot slot : data.slots()) {
            List<Student> cohort = data.studentsFor(slot.getPaperCode());
            Set<String> seated = plan.seating().seatsFor(slot.getId()).stream()
                    .map(Seat::studentId)
                    .collect(Collectors.toSet());

            assertEquals(cohort.size(), seated.size(), "slot " + slot.getId());
            assertTrue(seated.containsAll(cohort.stream().map(Student::getId).toList()));
        }
        assertEquals(128, plan.seating().seatedCount());
    }

    @Test
    void givesEveryRoomInUseExactlyOneInvigilator() {
        ExamPlan plan = allocator.allocate(data);

        for (ExamSlot slot : data.slots()) {
            Set<String> roomsInUse = plan.seating().seatsFor(slot.getId()).stream()
                    .map(Seat::roomId)
                    .collect(Collectors.toSet());
            List<Assignment> duties = plan.duties().forSlot(slot.getId());

            assertEquals(roomsInUse.size(), duties.size(), "slot " + slot.getId());
            assertEquals(roomsInUse, duties.stream().map(Assignment::roomId).collect(Collectors.toSet()));
        }
    }

    @Test
    void spreadsDutiesRatherThanPilingThemOnOnePerson() {
        ExamPlan plan = allocator.allocate(data);

        int busiest = data.faculty().stream()
                .map(Faculty::getId)
                .mapToInt(id -> plan.duties().forFaculty(id).size())
                .max()
                .orElseThrow();

        assertTrue(busiest <= 2, "one invigilator drew " + busiest + " duties");
    }

    @Test
    void givesTheSameAnswerEveryRun() {
        ExamPlan first = allocator.allocate(data);
        ExamPlan second = allocator.allocate(data);

        for (ExamSlot slot : data.slots()) {
            assertEquals(first.seating().seatsFor(slot.getId()), second.seating().seatsFor(slot.getId()));
        }
        assertEquals(first.duties().all(), second.duties().all());
    }
}
