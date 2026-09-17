package edsa.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The four input files, loaded and indexed. */
public final class ExamData {

    private final List<Student> students;
    private final List<Room> rooms;
    private final List<Faculty> faculty;
    private final List<ExamSlot> slots;
    private final Map<String, Student> studentsById;
    private final Map<String, Room> roomsById;
    private final Map<String, Faculty> facultyById;
    private final Map<String, ExamSlot> slotsById;

    public ExamData(List<Student> students, List<Room> rooms, List<Faculty> faculty, List<ExamSlot> slots) {
        this.students = List.copyOf(students);
        this.rooms = List.copyOf(rooms);
        this.faculty = List.copyOf(faculty);
        this.slots = List.copyOf(slots);
        this.studentsById = index(this.students, Student::getId);
        this.roomsById = index(this.rooms, Room::getId);
        this.facultyById = index(this.faculty, Faculty::getId);
        this.slotsById = index(this.slots, ExamSlot::getId);
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

    public List<ExamSlot> slots() {
        return slots;
    }

    public Student student(String id) {
        return studentsById.get(id);
    }

    public Room room(String id) {
        return roomsById.get(id);
    }

    public ExamSlot slot(String id) {
        return slotsById.get(id);
    }

    public Faculty facultyMember(String id) {
        return facultyById.get(id);
    }

    /** Everyone sitting one paper, in id order so the same input always gives the same output. */
    public List<Student> studentsFor(String paperCode) {
        return students.stream()
                .filter(student -> student.isRegisteredFor(paperCode))
                .sorted(Comparator.comparing(Student::getId))
                .toList();
    }
}
