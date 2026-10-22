package edsa.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * A guard against planning becoming accidentally slow, not a benchmark.
 *
 * <p>Measured on an ordinary machine, planning a session of thirty papers, three thousand
 * students, forty rooms and eighty invigilators — schedule, seating, duties and the
 * independent re-check — takes about twenty milliseconds. The ceilings below are two hundred
 * times that, so this cannot fail because a build machine was busy; it fails if somebody
 * turns an incremental conflict check back into a quadratic one, which is the mistake worth
 * catching.
 *
 * <p>This is also the evidence that the solver does not need running off the request thread:
 * at this scale it finishes well inside a single page load.
 */
class PlanningPerformanceTest {

    private static ExamData session(int papers, int studentsPerPaper, int rooms, int faculty) {
        List<Student> students = new ArrayList<>();
        List<Exam> exams = new ArrayList<>();
        LocalDate day = LocalDate.of(2026, 11, 10);
        for (int paper = 1; paper <= papers; paper++) {
            String code = "SUB" + paper;
            for (int index = 1; index <= studentsPerPaper; index++) {
                students.add(new Student(code + "-S" + index, "Student " + index, "CSE",
                        new LinkedHashSet<>(Set.of(code))));
            }
            exams.add(new Exam("E" + paper, code, code + " paper", day.plusDays(paper / 4), null, null));
        }
        List<Room> roomList = new ArrayList<>();
        for (int index = 1; index <= rooms; index++) {
            roomList.add(Room.ofCapacity("R" + index, "Room " + index, 30, "Block A"));
        }
        List<Faculty> staff = new ArrayList<>();
        for (int index = 1; index <= faculty; index++) {
            staff.add(new Faculty("F" + index, "Faculty " + index, "CSE", index % 3 == 0, Set.of()));
        }
        return new ExamData(students, roomList, staff, exams);
    }

    private static long millisToPlan(ExamData data) {
        new ExamPlanner().plan(data, ExamMode.ENDSEM);
        long start = System.nanoTime();
        new ExamPlanner().plan(data, ExamMode.ENDSEM);
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Test
    void aSessionTheSizeOfARealOneIsPlannedWellInsideOnePageLoad() {
        ExamData data = session(15, 60, 30, 60);

        long millis = millisToPlan(data);

        assertTrue(millis < 2_000, "planning 900 students took " + millis + " ms");
    }

    @Test
    void aSessionSeveralTimesLargerIsStillFast() {
        ExamData data = session(30, 100, 40, 80);

        long millis = millisToPlan(data);

        assertTrue(millis < 4_000, "planning 3000 students took " + millis + " ms");
    }

    @Test
    void theWholeSessionIsActuallyPlannedAndNotQuietlyDropped() {
        ExamData data = session(30, 100, 40, 80);

        PlanningResult result = new ExamPlanner().plan(data, ExamMode.ENDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        assertTrue(result.plan().seating().seatedCount() == 3_000,
                "every student should be seated, got " + result.plan().seating().seatedCount());
    }
}
