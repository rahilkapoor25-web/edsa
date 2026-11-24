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

    private ImportReport checkOf(byte[] workbook) {
        return imports.check("session.xlsx", workbook.length, new ByteArrayInputStream(workbook));
    }

    // ── The validity check behind the Generate schedule button ───────────────────────────

    @Test
    void checkingAValidWorkbookPassesAndStoresNothing() {
        ImportReport report = checkOf(WorkbookBuilder.valid().build());

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertEquals(3, report.students());
        assertEquals(2, report.exams());
        assertFalse(repository.hasData(), "a check must never write");
    }

    @Test
    void checkingRefusesWhatAnImportWouldRefuse() {
        ImportReport report = checkOf(WorkbookBuilder.valid().without("Rooms").build());

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("Rooms")), String.valueOf(report.errors()));
    }

    @Test
    void aWorkbookWithNoAvailableRoomCannotBeScheduled() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Rooms", List.of("Room ID", "Name", "Building", "Capacity", "Available"),
                        List.of(List.of("R101", "Hall One", "Block A", "30", "no")))
                .build();

        ImportReport report = checkOf(workbook);

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("No room")), String.valueOf(report.errors()));
    }

    @Test
    void aWorkbookWithNoAvailableInvigilatorCannotBeScheduled() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Faculty", List.of("Faculty ID", "Name", "Email", "Department", "Post", "Available"),
                        List.of(List.of("F01", "Faculty One", "f1@example.edu", "CSE", "Professor", "no")))
                .build();

        ImportReport report = checkOf(workbook);

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("invigilate")), String.valueOf(report.errors()));
    }

    // ── Rules taken from the reference sample workbook ───────────────────────────────────

    private static final List<String> EXAM_COLUMNS =
            List.of("Exam ID", "Subject Code", "Date", "Start Time", "Duration Minutes");
    private static final List<String> STUDENT_COLUMNS =
            List.of("Student ID", "Name", "Email", "Department", "Year", "Section");

    private static void assertRefusedFor(ImportReport report, String... fragments) {
        assertFalse(report.successful(), "expected a refusal");
        assertTrue(report.errors().stream().anyMatch(error -> List.of(fragments).stream().allMatch(error::contains)),
                "no error mentions " + List.of(fragments) + " in " + report.errors());
    }

    @Test
    void everyExamNeedsAStartTime() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Exams", EXAM_COLUMNS, List.of(
                        List.of("E1", "CS301", "2026-11-10", "09:00", ""),
                        List.of("E2", "EC301", "2026-11-10", "", "")))
                .build();

        assertRefusedFor(checkOf(workbook), "Exams", "row 3", "Start Time", "required");
    }

    @Test
    void theExamsSheetNeedsAStartTimeColumn() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Exams", List.of("Exam ID", "Subject Code", "Date"), List.of(
                        List.of("E1", "CS301", "2026-11-10"),
                        List.of("E2", "EC301", "2026-11-10")))
                .build();

        assertRefusedFor(checkOf(workbook), "Exams", "Start Time");
    }

    @Test
    void aStudentEmailThatIsFilledInMustBeAnEmailAddress() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Students", STUDENT_COLUMNS, List.of(
                        List.of("S001", "Example One", "not-an-address", "CSE", "3", "A"),
                        List.of("S002", "Example Two", "s2@example.edu", "CSE", "3", "A"),
                        List.of("S003", "Example Three", "s3@example.edu", "ECE", "3", "B")))
                .build();

        assertRefusedFor(checkOf(workbook), "Students", "Email", "not-an-address");
    }

    @Test
    void studentEmailYearAndSectionMayStillBeLeftBlank() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Students", STUDENT_COLUMNS, List.of(
                        List.of("S001", "Example One", "", "CSE", "", ""),
                        List.of("S002", "Example Two", "", "CSE", "", ""),
                        List.of("S003", "Example Three", "", "ECE", "", "")))
                .build();

        ImportReport report = checkOf(workbook);

        assertTrue(report.successful(), String.valueOf(report.errors()));
    }

    @Test
    void aYearThatIsFilledInMustBeAWholeNumber() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Students", STUDENT_COLUMNS, List.of(
                        List.of("S001", "Example One", "s1@example.edu", "CSE", "third", "A"),
                        List.of("S002", "Example Two", "s2@example.edu", "CSE", "3", "A"),
                        List.of("S003", "Example Three", "s3@example.edu", "ECE", "3", "B")))
                .build();

        assertRefusedFor(checkOf(workbook), "Students", "Year", "third");
    }

    @Test
    void aSubjectIsExaminedOnlyOnce() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Exams", EXAM_COLUMNS, List.of(
                        List.of("E1", "CS301", "2026-11-10", "09:00", ""),
                        List.of("E2", "EC301", "2026-11-10", "09:00", ""),
                        List.of("E9", "CS301", "2026-11-12", "09:00", "")))
                .build();

        assertRefusedFor(checkOf(workbook), "Exams", "E9", "CS301", "E1");
    }

    @Test
    void everyStudentIsEnrolledInSomething() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Enrollments", List.of("Student ID", "Subject Code"),
                        List.of(List.of("S001", "CS301"), List.of("S003", "EC301")))
                .build();

        assertRefusedFor(checkOf(workbook), "S002", "not enrolled");
    }

    @Test
    void anExamLargerThanEveryAvailableSeatIsRefused() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Rooms", List.of("Room ID", "Name", "Building", "Capacity", "Available"), List.of(
                        List.of("R101", "Hall One", "Block A", "1", "yes"),
                        List.of("R102", "Hall Two", "Block A", "40", "no")))
                .build();

        // CS301 has two students and only one available seat exists.
        assertRefusedFor(checkOf(workbook), "CS301", "2 students", "1");
    }

    @Test
    void aPaperNobodyIsEnrolledForIsRefusedByTheCheckAndTheImport() {
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Subjects", List.of("Subject Code", "Subject Name", "Department", "Semester"),
                        List.of(List.of("CS301", "Data Structures", "CSE", "5"),
                                List.of("EC301", "Signals", "ECE", "5"),
                                List.of("ME301", "Thermodynamics", "ME", "5")))
                .replace("Exams", List.of("Exam ID", "Subject Code", "Date", "Start Time", "Duration Minutes"),
                        List.of(List.of("E1", "CS301", "2026-11-10", "09:00", ""),
                                List.of("E3", "ME301", "2026-11-11", "09:00", "")))
                .build();

        ImportReport checked = checkOf(workbook);

        assertFalse(checked.successful());
        assertTrue(checked.errors().stream().anyMatch(error -> error.contains("E3") && error.contains("ME301")),
                String.valueOf(checked.errors()));
        assertFalse(importOf(workbook).successful());
        assertFalse(repository.hasData(), "a refused import leaves the database empty");
    }

    @Test
    void importsEveryWorksheetAndReportsTheCounts() {
        ImportReport report = importOf(WorkbookBuilder.valid().build());

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertEquals(3, report.students());
        assertEquals(5, report.faculty());
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
        assertEquals(4, data.faculty().size(), "the unavailable member is left out of the pool");
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
                .replace("Exams", List.of("Exam ID", "Subject Code", "Date", "Start Time"),
                        List.of(List.of("E1", "ZZ999", "2026-11-10", "09:00")))
                .build());

        assertFalse(report.successful());
        assertTrue(report.errors().stream().anyMatch(error ->
                        error.contains("'ZZ999' is not listed on the Subjects worksheet")),
                report.errors().toString());
    }

    @Test
    void refusesAnUnreadableDate() {
        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Exams", List.of("Exam ID", "Subject Code", "Date", "Start Time"),
                        List.of(List.of("E1", "CS301", "10-11-2026", "09:00")))
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
                // Every paper needs candidates, so the now-empty EC301 paper goes too.
                .replace("Exams", List.of("Exam ID", "Subject Code", "Date", "Start Time", "Duration Minutes"),
                        List.of(List.of("E1", "CS301", "2026-11-10", "09:00", "")))
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

    /** Staff from a third department, so either paper can always be given its Professor and partner. */
    private static final List<List<String>> SUPPORTING_STAFF = List.of(
            List.of("F90", "Support Professor", "f90@example.edu", "ME", "Professor", "yes"),
            List.of("F91", "Support Junior One", "f91@example.edu", "ME", "Assistant Professor", "yes"),
            List.of("F92", "Support Junior Two", "f92@example.edu", "ME", "Assistant Professor", "yes"));

    /**
     * The valid workbook with its Faculty worksheet replaced: the given rows (id, name, email,
     * department, available) become Professors, followed by the supporting staff.
     */
    private static byte[] withFacultyRows(java.util.List<java.util.List<String>> rows) {
        List<List<String>> all = new java.util.ArrayList<>();
        for (List<String> row : rows) {
            all.add(List.of(row.get(0), row.get(1), row.get(2), row.get(3), "Professor", row.get(4)));
        }
        all.addAll(SUPPORTING_STAFF);
        return WorkbookBuilder.valid()
                .replace("Faculty", List.of("Faculty ID", "Name", "Email", "Department", "Post", "Available"), all)
                .build();
    }

    @Test
    void theFacultyEmailIsImportedAndStored() {
        ImportReport report = importOf(WorkbookBuilder.valid().build());

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertEquals("f1@example.edu", jdbc.queryForObject(
                "SELECT email FROM faculty WHERE id = 'F01'", String.class));
        assertEquals(5, repository.facultyContacts().size(),
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
        assertEquals(1 + SUPPORTING_STAFF.size(), report.faculty());
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
                .replace("Faculty", List.of("Faculty ID", "Name", "Department", "Post", "Available"),
                        List.of(List.of("F01", "Faculty One", "CSE", "Professor", "yes"),
                                List.of("F90", "Support Professor", "ME", "Professor", "yes"),
                                List.of("F91", "Support Junior One", "ME", "Assistant Professor", "yes"),
                                List.of("F92", "Support Junior Two", "ME", "Assistant Professor", "yes")))
                .build());

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertFalse(repository.facultyContacts().get(0).hasEmail(),
                "Email stays optional, so an older workbook is not rejected");
    }
}
