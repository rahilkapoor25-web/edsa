package edsa.data;

import edsa.core.Exam;
import edsa.core.ExamData;
import edsa.core.Faculty;
import edsa.core.Room;
import edsa.core.Student;
import edsa.core.Subject;

import java.util.List;
import java.util.Map;

/**
 * Everything one workbook contained, after parsing and validation.
 *
 * <p>Availability is kept beside the records rather than inside them: an unavailable room or
 * invigilator is still imported, it is simply left out of the pool a plan may draw on. The
 * faculty email addresses are kept beside them for the same reason: {@code edsa.core} plans
 * examinations and has no use for a contact address, but the Accounts page does.
 */
public record ImportedWorkbook(List<Student> students,
                               List<Faculty> faculty,
                               List<Room> rooms,
                               List<Subject> subjects,
                               List<Exam> exams,
                               Map<String, List<String>> enrollments,
                               Map<String, Boolean> roomAvailability,
                               Map<String, Boolean> facultyAvailability,
                               Map<String, String> facultyEmails) {

    public int enrollmentCount() {
        return enrollments.values().stream().mapToInt(List::size).sum();
    }

    /** The planning view: only the rooms and invigilators that are actually available. */
    public ExamData toExamData() {
        return new ExamData(
                students,
                rooms.stream().filter(room -> roomAvailability.getOrDefault(room.getId(), true)).toList(),
                faculty.stream().filter(member -> facultyAvailability.getOrDefault(member.getId(), true)).toList(),
                exams);
    }
}
