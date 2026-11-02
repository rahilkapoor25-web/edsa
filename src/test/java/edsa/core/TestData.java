package edsa.core;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builders for small, obviously fictional scenarios. */
final class TestData {

    static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    private TestData() {
    }

    static List<Student> students(String prefix, int count, String... subjectCodes) {
        List<Student> students = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            students.add(new Student(prefix + index, "Student " + prefix + index, "CSE",
                    new LinkedHashSet<>(Set.of(subjectCodes))));
        }
        return students;
    }

    static List<Faculty> faculty(int count) {
        List<Faculty> faculty = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            faculty.add(new Faculty("F" + index, "Faculty " + index, "CSE", false, Set.of()));
        }
        return faculty;
    }

    /** Faculty on named ranks, so a weighted ladder has something to weight. */
    static List<Faculty> ranked(String... designations) {
        List<Faculty> faculty = new ArrayList<>();
        for (int index = 1; index <= designations.length; index++) {
            faculty.add(new Faculty("F" + index, "Faculty " + index, "CSE", false, Set.of(),
                    designations[index - 1], null));
        }
        return faculty;
    }

    /** One member who teaches the papers named, for the course-teacher rule. */
    static Faculty teaching(String id, String... subjectCodes) {
        return new Faculty(id, "Faculty " + id, "CSE", false,
                new LinkedHashSet<>(Set.of(subjectCodes)));
    }

    static List<Room> rooms(int count, int capacity) {
        List<Room> rooms = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            rooms.add(Room.ofCapacity("R" + index, "Room " + index, capacity, "Block A"));
        }
        return rooms;
    }

    static Exam exam(String id, String subjectCode, LocalTime permittedStart) {
        return new Exam(id, subjectCode, subjectCode + " paper", DAY, permittedStart, null);
    }

    static Exam exam(String id, String subjectCode, LocalTime permittedStart, Integer durationOverride) {
        return new Exam(id, subjectCode, subjectCode + " paper", DAY, permittedStart, durationOverride);
    }
}
