package edsa.data;

import edsa.core.Exam;
import edsa.core.ExamData;
import edsa.core.Faculty;
import edsa.core.Room;
import edsa.core.Student;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The imported reference data, held in the application database.
 *
 * <p>An import replaces the previous contents inside one transaction, so a workbook that
 * fails part way through leaves the database exactly as it was rather than half updated.
 */
@Repository
public class ExamDataRepository {

    private final JdbcTemplate jdbc;

    public ExamDataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceAll(ImportedWorkbook workbook) {
        jdbc.update("DELETE FROM enrollment");
        jdbc.update("DELETE FROM exam");
        jdbc.update("DELETE FROM subject");
        jdbc.update("DELETE FROM student");
        jdbc.update("DELETE FROM faculty");
        jdbc.update("DELETE FROM room");

        for (Student student : workbook.students()) {
            jdbc.update("INSERT INTO student (id, name, department) VALUES (?, ?, ?)",
                    student.getId(), student.getName(), student.getDepartment());
        }
        for (Faculty member : workbook.faculty()) {
            jdbc.update("INSERT INTO faculty (id, name, email, department, available) VALUES (?, ?, ?, ?, ?)",
                    member.getId(), member.getName(),
                    workbook.facultyEmails().get(member.getId()), member.getDepartment(),
                    workbook.facultyAvailability().getOrDefault(member.getId(), true));
        }
        for (Room room : workbook.rooms()) {
            jdbc.update("INSERT INTO room (id, name, building, capacity, available) VALUES (?, ?, ?, ?, ?)",
                    room.getId(), room.getName(), room.getBuilding(), room.capacity(),
                    workbook.roomAvailability().getOrDefault(room.getId(), true));
        }
        workbook.subjects().forEach(subject ->
                jdbc.update("INSERT INTO subject (code, name, department, semester) VALUES (?, ?, ?, ?)",
                        subject.code(), subject.name(), subject.department(), subject.semester()));
        workbook.enrollments().forEach((studentId, codes) -> codes.forEach(code ->
                jdbc.update("INSERT INTO enrollment (student_id, subject_code) VALUES (?, ?)", studentId, code)));
        for (Exam exam : workbook.exams()) {
            jdbc.update("INSERT INTO exam (id, subject_code, exam_date, permitted_start, duration_minutes) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    exam.getId(), exam.getSubjectCode(), Date.valueOf(exam.getDate()),
                    exam.getPermittedStart().map(Time::valueOf).orElse(null),
                    exam.getDurationOverrideMinutes().orElse(null));
        }
    }

    /**
     * Every imported faculty member with their contact details, available or not.
     *
     * <p>This is what the Accounts page provisions from: the records the administrator already
     * imported, read back by their stable ids. Unavailable members are included — somebody who
     * is not invigilating this session still needs to be able to sign in.
     */
    public List<FacultyContact> facultyContacts() {
        return jdbc.query("SELECT id, name, email, department FROM faculty ORDER BY id",
                (rs, row) -> new FacultyContact(rs.getString("id"), rs.getString("name"),
                        rs.getString("email"), rs.getString("department")));
    }

    public boolean hasData() {
        Long students = jdbc.queryForObject("SELECT COUNT(*) FROM student", Long.class);
        Long exams = jdbc.queryForObject("SELECT COUNT(*) FROM exam", Long.class);
        return students != null && students > 0 && exams != null && exams > 0;
    }

    /** Loads the planning view: available rooms and invigilators only. */
    public ExamData load() {
        Map<String, Set<String>> enrollments = new LinkedHashMap<>();
        jdbc.query("SELECT student_id, subject_code FROM enrollment ORDER BY student_id, subject_code",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        enrollments.computeIfAbsent(rs.getString("student_id"), key -> new LinkedHashSet<>())
                                .add(rs.getString("subject_code")));

        List<Student> students = jdbc.query("SELECT id, name, department FROM student ORDER BY id",
                (rs, row) -> new Student(rs.getString("id"), rs.getString("name"), rs.getString("department"),
                        enrollments.getOrDefault(rs.getString("id"), Set.of())));

        List<Room> rooms = jdbc.query(
                "SELECT id, name, building, capacity FROM room WHERE available = TRUE ORDER BY id",
                (rs, row) -> Room.ofCapacity(rs.getString("id"), rs.getString("name"),
                        rs.getInt("capacity"), rs.getString("building")));

        List<Faculty> faculty = jdbc.query(
                "SELECT id, name, department FROM faculty WHERE available = TRUE ORDER BY id",
                (rs, row) -> new Faculty(rs.getString("id"), rs.getString("name"), rs.getString("department"),
                        false, Set.of()));

        Map<String, String> subjectNames = new LinkedHashMap<>();
        jdbc.query("SELECT code, name FROM subject",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        subjectNames.put(rs.getString("code"), rs.getString("name")));

        List<Exam> exams = new ArrayList<>(jdbc.query(
                "SELECT id, subject_code, exam_date, permitted_start, duration_minutes FROM exam ORDER BY exam_date, id",
                (rs, row) -> {
                    Time start = rs.getTime("permitted_start");
                    int duration = rs.getInt("duration_minutes");
                    LocalDate date = rs.getDate("exam_date").toLocalDate();
                    LocalTime permitted = start == null ? null : start.toLocalTime();
                    return new Exam(rs.getString("id"), rs.getString("subject_code"),
                            subjectNames.getOrDefault(rs.getString("subject_code"), rs.getString("subject_code")),
                            date, permitted, rs.wasNull() || duration == 0 ? null : duration);
                }));

        return new ExamData(students, rooms, faculty, exams);
    }
}
