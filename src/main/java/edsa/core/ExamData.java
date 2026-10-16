package edsa.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The input the examination office supplies: who sits what, where, and when it may be sat. */
public final class ExamData {

    private final List<Student> students;
    private final List<Room> rooms;
    private final List<Faculty> faculty;
    private final List<Exam> exams;
    private final Map<String, Student> studentsById;
    private final Map<String, Room> roomsById;
    private final Map<String, Faculty> facultyById;
    private final Map<String, Exam> examsById;

    public ExamData(List<Student> students, List<Room> rooms, List<Faculty> faculty, List<Exam> exams) {
        this.students = List.copyOf(students);
        this.rooms = List.copyOf(rooms);
        this.faculty = List.copyOf(faculty);
        this.exams = List.copyOf(exams);
        this.studentsById = index(this.students, Student::getId);
        this.roomsById = index(this.rooms, Room::getId);
        this.facultyById = index(this.faculty, Faculty::getId);
        this.examsById = index(this.exams, Exam::getId);
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> idOf) {
        Map<String, T> byId = new LinkedHashMap<>();
        values.forEach(value -> byId.put(idOf.apply(value), value));
        return byId;
    }

    public List<Student> students() {
        return students;
    }

    public List<Room> rooms() {
        return rooms;
    }

    public List<Faculty> faculty() {
        return faculty;
    }

    public List<Exam> exams() {
        return exams;
    }

    public Student student(String id) {
        return studentsById.get(id);
    }

    public Room room(String id) {
        return roomsById.get(id);
    }

    public Faculty facultyMember(String id) {
        return facultyById.get(id);
    }

    public Exam exam(String id) {
        return examsById.get(id);
    }

    /** Everyone registered for one paper, in id order so the same input gives the same output. */
    public List<Student> studentsFor(String subjectCode) {
        return students.stream()
                .filter(student -> student.isRegisteredFor(subjectCode))
                .sorted(Comparator.comparing(Student::getId))
                .toList();
    }

    /**
     * The papers whose declared length this mode refuses, and what they will run for instead.
     *
     * <p>A Quiz is always ten minutes, so a {@code Duration Minutes} value left over from a
     * longer sitting is ignored. That is reported here rather than applied, so a page can tell
     * the coordinator the figure in the workbook was not used.
     */
    public List<String> ignoredDurationOverrides(ExamMode mode) {
        return exams.stream()
                .filter(exam -> exam.overrideIgnoredUnder(mode))
                .map(exam -> exam.getSubjectCode() + " (" + exam.getId() + ") declares "
                        + exam.getDurationOverrideMinutes().orElseThrow() + " minutes, but a "
                        + mode.label() + " paper runs for " + mode.defaultMinutes() + " minutes")
                .toList();
    }

    /** Capacity of a room, falling back to the configured assumption when it has none of its own. */
    public int capacityOf(String roomId, SchedulingConfig config) {
        Room room = roomsById.get(roomId);
        if (room == null) {
            return 0;
        }
        return room.capacity() > 0 ? room.capacity() : config.fallbackRoomCapacity();
    }
}
