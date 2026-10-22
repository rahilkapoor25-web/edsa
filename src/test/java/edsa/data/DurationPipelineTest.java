package edsa.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.ExamPlan;
import edsa.core.ExamPlanner;
import edsa.core.ExamSlot;
import edsa.core.PlanningResult;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * A stale {@code Duration Minutes} value, carried all the way from a real worksheet row to the
 * downloaded workbook.
 *
 * <p>This is the other half of the Quiz duration regression: {@code ExamDurationTest} proves
 * the core times every paper by the mode, and this proves the value survives neither the
 * import, the database round trip, the schedule, nor the export.
 */
@SpringBootTest
@ActiveProfiles("test")
class DurationPipelineTest {

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

    /** The valid workbook, with the Exams worksheet replaced by the given rows. */
    private static byte[] withExamRows(List<List<String>> rows) {
        return WorkbookBuilder.valid()
                .replace("Exams", List.of("Exam ID", "Subject Code", "Date", "Start Time", "Duration Minutes"), rows)
                .build();
    }

    @Test
    void aNinetyMinuteValueOnTheLastExamRowDoesNotStretchTheQuiz() {
        ImportReport report = importOf(withExamRows(List.of(
                List.of("E1", "CS301", "2026-11-10", "09:00", ""),
                List.of("E2", "EC301", "2026-11-10", "09:00", "90"))));
        assertTrue(report.successful(), String.valueOf(report.errors()));

        PlanningResult result = new ExamPlanner().plan(repository.load(), ExamMode.QUIZ);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        assertEquals(2, result.plan().schedule().size());
        for (ExamSlot slot : result.plan().schedule()) {
            assertEquals(10, slot.getDurationMinutes(), slot.getPaperCode() + " must be a ten-minute Quiz");
            assertEquals(slot.getStartTime().plusMinutes(10), slot.getEndTime());
        }
    }

    @Test
    void theSameValueIsIgnoredForMidsemAndEndsemToo() {
        importOf(withExamRows(List.of(
                List.of("E1", "CS301", "2026-11-10", "09:00", ""),
                List.of("E2", "EC301", "2026-11-10", "09:00", "90"))));
        ExamData data = repository.load();

        // Whichever mode is chosen, both papers run for that mode's length.
        assertEquals(List.of(120, 120), durationsByExamId(new ExamPlanner().plan(data, ExamMode.MIDSEM).plan()));
        assertEquals(List.of(180, 180), durationsByExamId(new ExamPlanner().plan(data, ExamMode.ENDSEM).plan()));
        assertEquals(List.of(10, 10), durationsByExamId(new ExamPlanner().plan(data, ExamMode.QUIZ).plan()));
    }

    /**
     * The reported bug end to end: an Exams worksheet where one paper carries 90, imported
     * and planned as an Endsem session. It used to come out at 09:00-10:30 in a timetable
     * headed "3 h".
     */
    @Test
    void aNinetyMinuteValueDoesNotShortenAnEndsemPaper() {
        importOf(withExamRows(List.of(
                List.of("E1", "CS301", "2026-11-10", "09:00", ""),
                List.of("E2", "EC301", "2026-11-10", "09:00", "90"))));

        PlanningResult result = new ExamPlanner().plan(repository.load(), ExamMode.ENDSEM);

        assertTrue(result.isUsable(), String.valueOf(result.problems()));
        for (ExamSlot slot : result.plan().schedule()) {
            assertEquals(180, slot.getDurationMinutes(), slot.getPaperCode());
            assertEquals("3 h", slot.getDurationLabel(), slot.getPaperCode());
            assertEquals(java.time.LocalTime.of(12, 0), slot.getEndTime(), slot.getPaperCode());
        }
    }

    @Test
    void theDeclaredLengthSurvivesTheDatabaseRoundTrip() {
        importOf(withExamRows(List.of(
                List.of("E1", "CS301", "2026-11-10", "09:00", "90"),
                List.of("E2", "EC301", "2026-11-10", "09:00", ""))));

        ExamData data = repository.load();

        assertEquals(90, data.exam("E1").getDurationOverrideMinutes().orElseThrow());
        assertTrue(data.exam("E2").getDurationOverrideMinutes().isEmpty());
        assertEquals(1, data.ignoredDurationOverrides(ExamMode.QUIZ).size(),
                "the stored value is reported as ignored, not applied");
        assertEquals(1, data.ignoredDurationOverrides(ExamMode.ENDSEM).size(),
                "and the same under Endsem, which no longer accepts one either");
    }

    @Test
    void theDownloadedWorkbookShowsTheQuizLengthNotTheWorkbookValue() {
        importOf(withExamRows(List.of(
                List.of("E1", "CS301", "2026-11-10", "09:00", ""),
                List.of("E2", "EC301", "2026-11-10", "09:00", "90"))));
        ExamPlan plan = new ExamPlanner().plan(repository.load(), ExamMode.QUIZ).plan();

        List<List<String>> timetable = readSheet(PlanWorkbook.write(plan), "Timetable");

        assertEquals(3, timetable.size(), "a header and two papers");
        for (List<String> row : timetable.subList(1, timetable.size())) {
            assertEquals("10 min", row.get(6), "the Duration column of " + row.get(1));
            assertEquals(row.get(4).substring(0, 2) + ":10", row.get(5), "the End column of " + row.get(1));
        }
    }

    @Test
    void theSeatingCsvCarriesTheSameTimesAsTheSchedule() {
        importOf(withExamRows(List.of(
                List.of("E1", "CS301", "2026-11-10", "09:00", "90"))));
        ExamPlan plan = new ExamPlanner().plan(repository.load(), ExamMode.QUIZ).plan();

        List<String> lines = SeatingPlanCsv.write(plan).lines().toList();

        assertTrue(lines.size() > 1, "at least one seat");
        assertEquals(10, plan.schedule().get(0).getDurationMinutes());
        lines.stream().skip(1).forEach(line ->
                assertTrue(line.contains(",09:00,"), "every seated row sits at the scheduled start: " + line));
    }

    @Test
    void refusesADeclaredLengthBeyondTheMaximum() {
        ImportReport report = importOf(withExamRows(List.of(
                List.of("E1", "CS301", "2026-11-10", "09:00", "400"))));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("column 'Duration Minutes': must be 300 or fewer"),
                report.errors().toString());
        assertFalse(repository.hasData(), "a refused workbook changes nothing");
    }

    @Test
    void refusesADeclaredLengthThatIsNotAWholeNumber() {
        ImportReport report = importOf(withExamRows(List.of(
                List.of("E1", "CS301", "2026-11-10", "09:00", "ninety"))));

        assertFalse(report.successful());
        assertTrue(report.errors().get(0).contains("'ninety' is not a whole number"), report.errors().toString());
    }

    private static List<Integer> durationsByExamId(ExamPlan plan) {
        return plan.schedule().stream()
                .sorted(java.util.Comparator.comparing(ExamSlot::getId))
                .map(ExamSlot::getDurationMinutes)
                .toList();
    }

    private static List<List<String>> readSheet(byte[] workbook, String name) {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            Sheet sheet = book.getSheet(name);
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                for (int column = 0; column < row.getLastCellNum(); column++) {
                    values.add(row.getCell(column) == null ? "" : row.getCell(column).getStringCellValue());
                }
                rows.add(values);
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot read the generated workbook", e);
        }
        return rows;
    }
}
