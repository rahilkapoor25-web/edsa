package edsa.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.ExamPlanner;
import edsa.core.PlanningResult;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Importing one workbook: what it accepts, what it refuses, and what it leaves behind. */
@SpringBootTest
@ActiveProfiles("test")
class WorkbookImportTest {

    @Autowired
    private WorkbookImportService imports;

    @Autowired
    private ExamDataRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTheDatabase() {
        jdbc.update("DELETE FROM enrollment");
        jdbc.update("DELETE FROM exam");
        jdbc.update("DELETE FROM subject");
        jdbc.update("DELETE FROM student");
        jdbc.update("DELETE FROM faculty");
        jdbc.update("DELETE FROM room");
    }

    private ImportReport importOf(byte[] workbook) {
        return imports.importWorkbook("session.xlsx", workbook.length, new ByteArrayInputStream(workbook));
    }

    @Test
    void importsEveryWorksheetAndReportsTheCounts() {
        ImportReport report = importOf(WorkbookBuilder.valid().build());

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertEquals(3, report.students());
        assertEquals(3, report.faculty());
        assertEquals(2, report.rooms());
        assertEquals(2, report.subjects());
        assertEquals(3, report.enrollments());
        assertEquals(2, report.exams());
    }

    @Test
    void importedDataSchedulesStraightAway() {
        importOf(WorkbookBuilder.valid().build());

        ExamData data = repository.load();
        PlanningResult result = new ExamPlanner().plan(data, ExamMode.MIDSEM);

        assertEquals(3, data.students().size());
        assertEquals(2, data.faculty().size(), "the unavailable member is left out of the pool");
        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        assertEquals(120, result.plan().schedule().get(0).getDurationMinutes());
    }

    @Test
    void refusesAWorkbookMissingAWorksheet() {
        ImportReport report = importOf(WorkbookBuilder.valid().without("Rooms").build());

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("no worksheet named 'Rooms'"), report.errors().toString());
        assertFalse(repository.hasData());
    }

    @Test
    void refusesAWorksheetMissingARequiredColumn() {
        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Rooms", List.of("Room ID", "Building"),
                        List.of(List.of("R101", "Block A")))
                .build());

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("missing the column 'Capacity'")),
                report.errors().toString());
    }

    @Test
    void namesTheWorksheetRowAndFieldOfABadValue() {
        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Rooms", List.of("Room ID", "Building", "Capacity"),
                        List.of(List.of("R101", "Block A", "30"), List.of("R102", "Block A", "zero")))
                .build());

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error ->
                        error.contains("Worksheet 'Rooms', row 3, column 'Capacity'")),
                report.errors().toString());
    }

    @Test
    void refusesANonPositiveRoomCapacity() {
        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Rooms", List.of("Room ID", "Building", "Capacity"),
                        List.of(List.of("R101", "Block A", "0")))
                .build());

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("greater than zero")),
                report.errors().toString());
    }

    @Test
    void refusesADuplicateStudentId() {
        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Students", List.of("Student ID", "Name", "Department"),
                        List.of(List.of("S001", "One", "CSE"), List.of("S001", "One Again", "CSE")))
                .build());

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("appears more than once")),
                report.errors().toString());
    }

    @Test
    void refusesAnEnrollmentForAnUnknownStudent() {
        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Enrollments", List.of("Student ID", "Subject Code"),
                        List.of(List.of("S999", "CS301")))
                .build());

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error ->
                        error.contains("'S999' is not listed on the Students worksheet")),
                report.errors().toString());
    }

    @Test
    void refusesAnExamForAnUnknownSubject() {
        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Exams", List.of("Exam ID", "Subject Code", "Date"),
                        List.of(List.of("E1", "ZZ999", "2026-11-10")))
                .build());

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error ->
                        error.contains("'ZZ999' is not listed on the Subjects worksheet")),
                report.errors().toString());
    }

    @Test
    void refusesAnUnreadableDate() {
        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Exams", List.of("Exam ID", "Subject Code", "Date"),
                        List.of(List.of("E1", "CS301", "10-11-2026")))
                .build());

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("is not a date")),
                report.errors().toString());
    }

    @Test
    void refusesAnythingThatIsNotAnXlsxFile() {
        ImportReport report = imports.importWorkbook("notes.txt", 12,
                new ByteArrayInputStream("not a workbook".getBytes()));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("Only .xlsx"), report.errors().toString());
    }

    @Test
    void refusesAWorkbookLargerThanTheLimit() {
        ImportReport report = imports.importWorkbook("huge.xlsx",
                WorkbookImportService.MAX_UPLOAD_BYTES + 1, new ByteArrayInputStream(new byte[0]));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("larger than"), report.errors().toString());
    }

    @Test
    void aFailedImportLeavesTheEarlierDataUntouched() {
        assertTrue(importOf(WorkbookBuilder.valid().build()).successful());
        long studentsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM student", Long.class);

        ImportReport failed = importOf(WorkbookBuilder.valid()
                .replace("Enrollments", List.of("Student ID", "Subject Code"),
                        List.of(List.of("S404", "CS301")))
                .build());

        assertFalse(failed.successful());
        assertEquals(studentsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM student", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM exam", Long.class));
        assertTrue(repository.hasData());
    }

    @Test
    void aSuccessfulImportReplacesWhatWasThereBefore() {
        importOf(WorkbookBuilder.valid().build());

        ImportReport second = importOf(WorkbookBuilder.valid()
                .replace("Students", List.of("Student ID", "Name", "Department"),
                        List.of(List.of("S001", "Only One", "CSE")))
                .replace("Enrollments", List.of("Student ID", "Subject Code"),
                        List.of(List.of("S001", "CS301")))
                .build());

        assertTrue(second.successful(), String.valueOf(second.errors()));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM student", Long.class));
    }

    @Test
    void theTemplateItShipsCanBeImportedAsIs() {
        ImportReport report = imports.importWorkbook("edsa-import-template.xlsx",
                ExcelTemplate.write().length, new ByteArrayInputStream(ExcelTemplate.write()));

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertEquals(3, report.students());
        assertEquals(3, report.exams());
    }

    // ── The Faculty worksheet's Email column ─────────────────────────────────────────────

    /** The valid workbook with its Faculty worksheet replaced. */
    private static byte[] withFacultyRows(java.util.List<java.util.List<String>> rows) {
        return WorkbookBuilder.valid()
                .replace("Faculty", List.of("Faculty ID", "Name", "Email", "Department", "Available"), rows)
                .build();
    }

    @Test
    void theFacultyEmailIsImportedAndStored() {
        ImportReport report = importOf(WorkbookBuilder.valid().build());

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertEquals("f1@example.edu", jdbc.queryForObject(
                "SELECT email FROM faculty WHERE id = 'F01'", String.class));
        assertEquals(3, repository.facultyContacts().size(),
                "an unavailable member still needs to be able to sign in");
        assertTrue(repository.facultyContacts().stream().allMatch(FacultyContact::hasEmail));
    }

    @Test
    void theEmailIsStoredLowerCased() {
        importOf(withFacultyRows(List.of(
                List.of("F01", "Faculty One", "Mixed.Case@Example.EDU", "CSE", "yes"))));

        assertEquals("mixed.case@example.edu", jdbc.queryForObject(
                "SELECT email FROM faculty WHERE id = 'F01'", String.class));
    }

    @Test
    void aFacultyMemberWithNoEmailIsStillImported() {
        ImportReport report = importOf(withFacultyRows(List.of(
                List.of("F01", "Faculty One", "", "CSE", "yes"))));

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertEquals(1, report.faculty());
        FacultyContact stored = repository.facultyContacts().get(0);
        assertFalse(stored.hasEmail(), "they simply cannot be given an account");
    }

    @Test
    void aMalformedFacultyEmailIsReportedWithItsRow() {
        ImportReport report = importOf(withFacultyRows(List.of(
                List.of("F01", "Faculty One", "f1@example.edu", "CSE", "yes"),
                List.of("F02", "Faculty Two", "not-an-address", "ECE", "yes"))));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("Worksheet 'Faculty', row 3, column 'Email'"),
                report.errors().toString());
        assertTrue(report.errors().get(0).contains("is not an email address"), report.errors().toString());
        assertFalse(repository.hasData(), "a refused workbook changes nothing");
    }

    @Test
    void twoFacultyRowsSharingOneEmailAreReported() {
        ImportReport report = importOf(withFacultyRows(List.of(
                List.of("F01", "Faculty One", "shared@example.edu", "CSE", "yes"),
                List.of("F02", "Faculty Two", "shared@example.edu", "ECE", "yes"))));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("already used by F01"), report.errors().toString());
    }

    @Test
    void aDuplicateFacultyIdIsStillReported() {
        ImportReport report = importOf(withFacultyRows(List.of(
                List.of("F01", "Faculty One", "f1@example.edu", "CSE", "yes"),
                List.of("F01", "Faculty One Again", "f1b@example.edu", "CSE", "yes"))));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("'F01' appears more than once"), report.errors().toString());
    }

    @Test
    void aWorkbookWithoutAnEmailColumnStillImports() {
        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Faculty", List.of("Faculty ID", "Name", "Department", "Available"),
                        List.of(List.of("F01", "Faculty One", "CSE", "yes")))
                .build());

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertFalse(repository.facultyContacts().get(0).hasEmail(),
                "Email stays optional, so an older workbook is not rejected");
    }
}
