package edsa;

import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.ExamPlan;
import edsa.core.ExamPlanner;
import edsa.core.Faculty;
import edsa.core.PlacedStudent;
import edsa.core.PlanningResult;
import edsa.core.RoomSitting;
import edsa.data.CsvReader;
import edsa.data.SampleData;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Drives the engine from the command line, without the web layer.
 *
 * <p>With no arguments it plans the bundled sample files; otherwise it takes the four input
 * files in the order students, rooms, faculty, timetable. An optional last argument picks the
 * exam mode (QUIZ, MIDSEM or ENDSEM); ENDSEM is the default.
 */
public final class CliRunner {

    private CliRunner() {
    }

    public static void main(String[] args) {
        List<String> arguments = new ArrayList<>(List.of(args));
        ExamMode mode = ExamMode.ENDSEM;
        if (!arguments.isEmpty()) {
            try {
                mode = ExamMode.of(arguments.get(arguments.size() - 1));
                arguments.remove(arguments.size() - 1);
            } catch (IllegalArgumentException keepDefault) {
                // the last argument is a file, not a mode
            }
        }
        if (arguments.size() != 0 && arguments.size() != 4) {
            System.err.println("usage: CliRunner [students.csv rooms.csv faculty.csv timetable.csv] [QUIZ|MIDSEM|ENDSEM]");
            System.exit(1);
        }

        ExamData data = arguments.size() == 4 ? readFiles(arguments) : SampleData.load();
        PlanningResult result = new ExamPlanner().plan(data, mode);
        print(result, mode);
        if (!result.isUsable()) {
            System.exit(2);
        }
    }

    private static ExamData readFiles(List<String> args) {
        CsvReader reader = new CsvReader();
        return new ExamData(
                reader.readStudents(Path.of(args.get(0))),
                reader.readRooms(Path.of(args.get(1))),
                reader.readFaculty(Path.of(args.get(2))),
                reader.readExams(Path.of(args.get(3))));
    }

    private static void print(PlanningResult result, ExamMode mode) {
        ExamPlan plan = result.plan();
        System.out.printf("EDSA - %s timetable (%d minutes per paper)%n", mode.label(), mode.defaultMinutes());

        for (RoomSitting sitting : plan.sittings()) {
            System.out.printf("%n%s  %s %s  %s %s-%s%n",
                    sitting.slot().getId(), sitting.slot().getPaperCode(), sitting.slot().getPaperName(),
                    sitting.slot().getDate(), sitting.slot().getStartTime(), sitting.slot().getEndTime());
            System.out.printf("  %s (%s) - %s - %d students%n",
                    sitting.room().getName(), sitting.room().getId(),
                    invigilators(sitting), sitting.seated());
            printGrid(sitting.students());
        }

        System.out.printf("%nSummary%n");
        System.out.printf("  papers scheduled : %d%n", plan.schedule().size());
        System.out.printf("  students seated  : %d%n", plan.seating().seatedCount());
        System.out.printf("  duties assigned  : %d%n", plan.duties().all().size());

        List<String> problems = result.problems();
        if (problems.isEmpty()) {
            System.out.println("  problems         : none");
        } else {
            System.out.println("  problems         :");
            problems.forEach(problem -> System.out.println("    - " + problem));
        }

        System.out.println("  duties per invigilator:");
        dutyCounts(plan).forEach((facultyId, count) -> {
            Faculty member = plan.data().facultyMember(facultyId);
            System.out.printf("    %-4s %-22s %d%n", facultyId, member.getName(), count);
        });
    }

    private static String invigilators(RoomSitting sitting) {
        if (sitting.invigilators().isEmpty()) {
            return "no invigilator";
        }
        return String.join(", ", sitting.invigilators().stream()
                .map(member -> member.getName() + " (" + member.getId() + ")").toList());
    }

    private static void printGrid(List<PlacedStudent> students) {
        Map<Integer, List<PlacedStudent>> byRow = new TreeMap<>();
        students.forEach(placed -> byRow.computeIfAbsent(placed.row(), key -> new ArrayList<>()).add(placed));
        byRow.forEach((row, rowStudents) -> {
            StringBuilder line = new StringBuilder("    ");
            rowStudents.forEach(placed -> line.append(placed.student().getId()).append(' '));
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
