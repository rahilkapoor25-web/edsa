package edsa;

import edsa.core.Assignment;
import edsa.core.ExamData;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;
import edsa.core.Faculty;
import edsa.core.GreedyAllocator;
import edsa.core.PlanChecker;
import edsa.core.Room;
import edsa.core.Seat;
import edsa.core.Violation;
import edsa.data.CsvReader;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Drives the engine from the command line, without the web layer.
 *
 * <p>With no arguments it runs on the bundled sample files; otherwise it takes the four input
 * files in the order students, rooms, faculty, timetable.
 */
public final class CliRunner {

    private CliRunner() {
    }

    public static void main(String[] args) {
        if (args.length != 0 && args.length != 4) {
            System.err.println("usage: CliRunner [students.csv rooms.csv faculty.csv timetable.csv]");
            System.exit(1);
        }

        CsvReader reader = new CsvReader();
        Path students = args.length == 4 ? Path.of(args[0]) : sample("students.csv");
        Path rooms = args.length == 4 ? Path.of(args[1]) : sample("rooms.csv");
        Path faculty = args.length == 4 ? Path.of(args[2]) : sample("faculty.csv");
        Path timetable = args.length == 4 ? Path.of(args[3]) : sample("timetable.csv");

        ExamData data = new ExamData(
                reader.readStudents(students),
                reader.readRooms(rooms),
                reader.readFaculty(faculty),
                reader.readSlots(timetable));

        PlanChecker checker = PlanChecker.standard();
        ExamPlan plan = new GreedyAllocator(checker).allocate(data);
        print(plan, checker);
    }

    private static Path sample(String name) {
        URL resource = CliRunner.class.getResource("/sample/" + name);
        if (resource == null) {
            throw new IllegalStateException("sample file is missing from the classpath: " + name);
        }
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot read sample file " + name, e);
        }
    }

    private static void print(ExamPlan plan, PlanChecker checker) {
        ExamData data = plan.data();
        System.out.println("EDSA - exam seating and invigilation plan");

        for (ExamSlot slot : data.slots()) {
            System.out.printf("%n%s  %s %s  %s %s-%s%n",
                    slot.getId(), slot.getPaperCode(), slot.getPaperName(),
                    slot.getDate(), slot.getStartTime(), slot.getEndTime());

            Map<String, List<Seat>> seatsByRoom = new LinkedHashMap<>();
            for (Seat seat : plan.seating().seatsFor(slot.getId())) {
                seatsByRoom.computeIfAbsent(seat.roomId(), key -> new ArrayList<>()).add(seat);
            }
            seatsByRoom.forEach((roomId, seats) -> {
                Room room = data.room(roomId);
                System.out.printf("  %s (%s) - %s - %d students%n",
                        room.getName(), roomId, invigilatorsFor(plan, slot.getId(), roomId), seats.size());
                printGrid(seats);
            });
        }

        System.out.printf("%nSummary%n");
        System.out.printf("  students seated : %d%n", plan.seating().seatedCount());
        System.out.printf("  duties assigned : %d%n", plan.duties().all().size());
        System.out.printf("  score           : %d%n", checker.score(plan));

        List<Violation> violations = checker.check(plan);
        if (violations.isEmpty()) {
            System.out.println("  violations      : none");
        } else {
            System.out.println("  violations      :");
            violations.forEach(violation -> System.out.println("    " + violation));
        }

        System.out.println("  duties per invigilator:");
        dutyCounts(plan).forEach((facultyId, count) -> {
            Faculty member = data.facultyMember(facultyId);
            System.out.printf("    %-4s %-22s %d%n", facultyId, member.getName(), count);
        });
    }

    private static String invigilatorsFor(ExamPlan plan, String slotId, String roomId) {
        Set<String> names = new LinkedHashSet<>();
        for (Assignment duty : plan.duties().forSlot(slotId)) {
            if (duty.roomId().equals(roomId)) {
                Faculty member = plan.data().facultyMember(duty.facultyId());
                names.add(member.getName() + " (" + member.getId() + ")");
            }
        }
        return names.isEmpty() ? "no invigilator" : String.join(", ", names);
    }

    private static void printGrid(List<Seat> seats) {
        Map<Integer, List<Seat>> byRow = new TreeMap<>();
        seats.forEach(seat -> byRow.computeIfAbsent(seat.row(), key -> new ArrayList<>()).add(seat));
        byRow.forEach((row, rowSeats) -> {
            StringBuilder line = new StringBuilder("    ");
            rowSeats.forEach(seat -> line.append(seat.studentId()).append(' '));
            System.out.println(line.toString().stripTrailing());
        });
    }

    private static Map<String, Integer> dutyCounts(ExamPlan plan) {
        Map<String, Integer> counts = new TreeMap<>();
        plan.data().faculty().forEach(member -> counts.put(member.getId(), 0));
        plan.duties().all().forEach(duty -> counts.merge(duty.facultyId(), 1, Integer::sum));
        return counts;
    }
}
