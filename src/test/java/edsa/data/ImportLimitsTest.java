package edsa.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What the importer refuses, and what it still accepts.
 *
 * <p>An upload is untrusted in size as well as in content. The point of these tests is that a
 * workbook which is too big is turned away with something the coordinator can act on, and
 * that a perfectly ordinary one is not caught by the same net.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportLimitsTest {

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
    void anOrdinaryWorkbookIsStillAccepted() {
        ImportReport report = importOf(WorkbookBuilder.valid().build());

        assertTrue(report.successful(), String.valueOf(report.errors()));
        assertTrue(repository.hasData());
    }

    @Test
    void aWorksheetWithTooManyRowsIsRefusedByName() {
        List<List<String>> manyRows = new ArrayList<>();
        for (int index = 0; index <= ExcelWorkbookReader.MAX_ROWS_PER_SHEET; index++) {
            manyRows.add(List.of("S" + index, "Student " + index, "s" + index + "@example.edu", "CSE"));
        }
        byte[] workbook = WorkbookBuilder.valid()
                .replace("Students", List.of("Student ID", "Name", "Email", "Department"), manyRows)
                .build();

        ImportReport report = importOf(workbook);

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("Worksheet 'Students'"), report.errors().toString());
        assertTrue(report.errors().get(0).contains("more than"), report.errors().toString());
        assertFalse(repository.hasData(), "a refused workbook leaves the database alone");
    }

    @Test
    void theRowLimitIsReportedRatherThanSilentlyTruncating() {
        List<List<String>> manyRows = new ArrayList<>();
        for (int index = 0; index <= ExcelWorkbookReader.MAX_ROWS_PER_SHEET; index++) {
            manyRows.add(List.of("S" + index, "Student " + index, "s" + index + "@example.edu", "CSE"));
        }

        ImportReport report = importOf(WorkbookBuilder.valid()
                .replace("Students", List.of("Student ID", "Name", "Email", "Department"), manyRows)
                .build());

        assertEquals(0, report.students(), "nothing is imported from a refused workbook");
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("50000")),
                "the message should say what the limit is: " + report.errors());
    }

    @Test
    void anUploadLargerThanTheByteLimitIsRefusedBeforeItIsOpened() {
        ImportReport report = imports.importWorkbook("session.xlsx",
                WorkbookImportService.MAX_UPLOAD_BYTES + 1, new ByteArrayInputStream(new byte[0]));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("larger than"), report.errors().toString());
    }

    @Test
    void anEmptyUploadIsRefused() {
        ImportReport report = imports.importWorkbook("session.xlsx", 0, new ByteArrayInputStream(new byte[0]));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("empty"), report.errors().toString());
    }

    @Test
    void somethingThatIsNotAWorkbookIsRefusedWithoutAStackTrace() {
        byte[] notAWorkbook = "this is not a spreadsheet at all".getBytes();

        ImportReport report = importOf(notAWorkbook);

        assertFalse(report.successful());
        String message = report.errors().get(0);
        assertTrue(message.contains("could not be read as an .xlsx workbook"), message);
        assertFalse(message.contains("org.apache.poi"), "the message is for a person, not a log: " + message);
        assertFalse(message.contains("Exception"), message);
    }

    @Test
    void aFileThatIsNotNamedXlsxIsRefused() {
        ImportReport report = imports.importWorkbook("session.xls", 100,
                new ByteArrayInputStream(new byte[] {1, 2, 3}));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("Only .xlsx"), report.errors().toString());
    }
}
