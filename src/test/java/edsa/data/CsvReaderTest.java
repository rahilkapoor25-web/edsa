package edsa.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.ExamSlot;
import edsa.core.Faculty;
import edsa.core.InvalidInputException;
import edsa.core.Room;
import edsa.core.Student;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvReaderTest {

    private final CsvReader reader = new CsvReader();

    @TempDir
    Path tempDir;

    private static Path sample(String name) throws URISyntaxException {
        return Path.of(CsvReaderTest.class.getResource("/sample/" + name).toURI());
    }

    private Path csv(String name, String... lines) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, List.of(lines));
        return file;
    }

    @Nested
    class SampleFiles {

        @Test
        void readsEveryStudent() throws Exception {
            List<Student> students = reader.readStudents(sample("students.csv"));

            assertEquals(60, students.size());
            Student first = students.get(0);
            assertEquals("S001", first.getId());
            assertEquals("Aarav Sharma", first.getName());
            assertEquals("CSE", first.getDepartment());
            assertEquals(Set.of("CS301", "CS302", "CS303"), first.getPaperCodes());
            assertTrue(first.isRegisteredFor("CS302"));
            assertFalse(first.isRegisteredFor("ME301"));
        }

        @Test
        void readsEveryRoom() throws Exception {
            List<Room> rooms = reader.readRooms(sample("rooms.csv"));

            assertEquals(4, rooms.size());
            Room first = rooms.get(0);
            assertEquals("R101", first.getId());
            assertEquals(48, first.capacity());
            assertEquals("Block A", first.getBuilding());
            assertEquals(163, rooms.stream().mapToInt(Room::capacity).sum());
        }

        @Test
        void readsEveryFacultyMember() throws Exception {
            List<Faculty> faculty = reader.readFaculty(sample("faculty.csv"));

            assertEquals(8, faculty.size());
            Faculty first = faculty.get(0);
            assertEquals("F01", first.getId());
            assertTrue(first.isSenior());
            assertTrue(first.teaches("CS301"));
            assertFalse(first.teaches("ME301"));
            assertEquals(4, faculty.stream().filter(Faculty::isSenior).count());
        }

        @Test
        void readsInvigilatorWhoTeachesNothing() throws Exception {
            Faculty last = reader.readFaculty(sample("faculty.csv")).get(7);

            assertEquals("F08", last.getId());
            assertTrue(last.getSubjectCodes().isEmpty());
        }

        @Test
        void readsEverySlot() throws Exception {
            List<ExamSlot> slots = reader.readSlots(sample("timetable.csv"));

            assertEquals(6, slots.size());
            ExamSlot first = slots.get(0);
            assertEquals("E1", first.getId());
            assertEquals("CS301", first.getPaperCode());
            assertEquals("Data Structures", first.getPaperName());
            assertEquals(LocalDate.of(2026, 11, 10), first.getDate());
            assertEquals(LocalTime.of(9, 30), first.getStartTime());
            assertEquals(LocalTime.of(12, 30), first.getEndTime());
        }

        @Test
        void everyPaperStudentsSitAppearsInTheTimetable() throws Exception {
            Set<String> timetabled = reader.readSlots(sample("timetable.csv")).stream()
                    .map(ExamSlot::getPaperCode)
                    .collect(Collectors.toSet());

            reader.readStudents(sample("students.csv")).forEach(student ->
                    assertTrue(timetabled.containsAll(student.getPaperCodes()),
                            student.getId() + " sits a paper that is not timetabled"));
        }
    }

    @Nested
    class MalformedRows {

        @Test
        void rejectsTooFewColumns() throws Exception {
            Path file = csv("students.csv",
                    "student_id,name,department,papers",
                    "S001,Aarav Sharma,CSE,CS301",
                    "S002,Vivaan Gupta,CSE");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readStudents(file));

            assertEquals("students.csv", thrown.getFileName());
            assertEquals(3, thrown.getLineNumber());
            assertTrue(thrown.getMessage().contains("expected 4 columns, found 3"),
                    thrown.getMessage());
        }

        @Test
        void rejectsBlankId() throws Exception {
            Path file = csv("students.csv",
                    "student_id,name,department,papers",
                    ",Aarav Sharma,CSE,CS301");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readStudents(file));

            assertEquals(2, thrown.getLineNumber());
            assertTrue(thrown.getMessage().contains("student_id is blank"), thrown.getMessage());
        }

        @Test
        void rejectsStudentWithNoPapers() throws Exception {
            Path file = csv("students.csv",
                    "student_id,name,department,papers",
                    "S001,Aarav Sharma,CSE,");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readStudents(file));

            assertTrue(thrown.getMessage().contains("papers is empty"), thrown.getMessage());
        }

        @Test
        void rejectsDuplicateId() throws Exception {
            Path file = csv("students.csv",
                    "student_id,name,department,papers",
                    "S001,Aarav Sharma,CSE,CS301",
                    "S001,Vivaan Gupta,CSE,CS301");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readStudents(file));

            assertEquals(3, thrown.getLineNumber());
            assertTrue(thrown.getMessage().contains("duplicate id S001"), thrown.getMessage());
        }

        @Test
        void rejectsNonNumericRoomRows() throws Exception {
            Path file = csv("rooms.csv",
                    "room_id,name,rows,columns,building",
                    "R101,Lecture Hall 1,eight,6,Block A");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readRooms(file));

            assertEquals("rooms.csv", thrown.getFileName());
            assertEquals(2, thrown.getLineNumber());
            assertTrue(thrown.getMessage().contains("rows is not a whole number"), thrown.getMessage());
        }

        @Test
        void rejectsRoomWithNoColumns() throws Exception {
            Path file = csv("rooms.csv",
                    "room_id,name,rows,columns,building",
                    "R101,Lecture Hall 1,8,0,Block A");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readRooms(file));

            assertTrue(thrown.getMessage().contains("columns must be greater than zero"),
                    thrown.getMessage());
        }

        @Test
        void rejectsUnrecognisedSeniorFlag() throws Exception {
            Path file = csv("faculty.csv",
                    "faculty_id,name,department,senior,subjects",
                    "F01,Dr. Meera Nair,CSE,yes,CS301");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readFaculty(file));

            assertEquals("faculty.csv", thrown.getFileName());
            assertTrue(thrown.getMessage().contains("senior must be true or false"), thrown.getMessage());
        }

        @Test
        void rejectsUnparseableDate() throws Exception {
            Path file = csv("timetable.csv",
                    "slot_id,paper_code,paper_name,exam_date,start_time,end_time",
                    "E1,CS301,Data Structures,10-11-2026,09:30,12:30");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readSlots(file));

            assertTrue(thrown.getMessage().contains("exam_date is not a date"), thrown.getMessage());
        }

        @Test
        void rejectsUnparseableTime() throws Exception {
            Path file = csv("timetable.csv",
                    "slot_id,paper_code,paper_name,exam_date,start_time,end_time",
                    "E1,CS301,Data Structures,2026-11-10,half nine,12:30");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readSlots(file));

            assertTrue(thrown.getMessage().contains("start_time is not a time"), thrown.getMessage());
        }

        @Test
        void rejectsSlotThatEndsBeforeItStarts() throws Exception {
            Path file = csv("timetable.csv",
                    "slot_id,paper_code,paper_name,exam_date,start_time,end_time",
                    "E1,CS301,Data Structures,2026-11-10,12:30,09:30");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readSlots(file));

            assertEquals(2, thrown.getLineNumber());
            assertTrue(thrown.getMessage().contains("is not after start_time"), thrown.getMessage());
        }

        @Test
        void rejectsEmptyFile() throws Exception {
            Path file = csv("students.csv");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readStudents(file));

            assertEquals(1, thrown.getLineNumber());
            assertTrue(thrown.getMessage().contains("file is empty"), thrown.getMessage());
        }

        @Test
        void reportsTheLineNumberOfTheFileNotTheRowNumber() throws Exception {
            Path file = csv("students.csv",
                    "student_id,name,department,papers",
                    "S001,Aarav Sharma,CSE,CS301",
                    "S002,Vivaan Gupta,CSE,CS301",
                    "S003,Aditya Rao,CSE,CS301",
                    "S004,Vihaan Nair,CSE,");

            InvalidInputException thrown =
                    assertThrows(InvalidInputException.class, () -> reader.readStudents(file));

            assertEquals(5, thrown.getLineNumber());
        }

        @Test
        void skipsBlankLines() throws Exception {
            Path file = csv("students.csv",
                    "student_id,name,department,papers",
                    "S001,Aarav Sharma,CSE,CS301",
                    "",
                    "S002,Vivaan Gupta,CSE,CS301");

            assertEquals(2, reader.readStudents(file).size());
        }
    }
}
