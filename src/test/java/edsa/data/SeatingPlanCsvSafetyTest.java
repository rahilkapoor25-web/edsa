package edsa.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.Assignment;
import edsa.core.DutyRoster;
import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;
import edsa.core.Exam;
import edsa.core.Faculty;
import edsa.core.Room;
import edsa.core.Seat;
import edsa.core.SeatingPlan;
import edsa.core.Student;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The seating CSV is opened in a spreadsheet, so a name out of an uploaded workbook is
 * untrusted input to that spreadsheet.
 *
 * <p>A cell beginning {@code =}, {@code +}, {@code -} or {@code @} is evaluated as a formula
 * when a CSV is opened, so a student recorded as {@code =HYPERLINK(...)} would run rather than
 * be read. These tests pin down that such a value is defused, that an ordinary name is left
 * exactly as it was, and that commas, quotes and line breaks still escape correctly.
 */
class SeatingPlanCsvSafetyTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    /** One student in one seat, named whatever the test wants to put in the workbook. */
    private static ExamPlan planWithStudentNamed(String name) {
        Student student = new Student("S1", name, "CSE", Set.of("CS301"));
        ExamData data = new ExamData(
                List.of(student),
                List.of(Room.ofCapacity("R1", "Room 1", 10, "Block A")),
                List.of(new Faculty("F1", "Faculty One", "CSE", false, Set.of())),
                List.of(new Exam("E1", "CS301", "CS301 paper", DAY, LocalTime.of(9, 0), null)));

        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        DutyRoster duties = new DutyRoster();
        duties.assign(new Assignment("E1", "R1", "F1"));

        return new ExamPlan(data, ExamMode.MIDSEM,
                List.of(new ExamSlot("E1", "CS301", "CS301 paper", DAY, LocalTime.of(9, 0), 120)),
                seating, duties);
    }

    /**
     * The single seat row, header removed.
     *
     * <p>Deliberately not split on newlines: a correctly quoted field may legitimately
     * contain one, so counting physical lines would be the wrong way to find the row.
     */
    private static String seatRow(String studentName) {
        String csv = SeatingPlanCsv.write(planWithStudentNamed(studentName));
        String body = csv.substring(csv.indexOf('\n') + 1);
        assertTrue(body.endsWith("\n"), "the row is terminated");
        return body.substring(0, body.length() - 1);
    }

    /** The student_name column is last, so whatever follows the student id is the name. */
    private static String exportedName(String studentName) {
        String row = seatRow(studentName);
        return row.substring(row.indexOf(",S1,") + ",S1,".length());
    }

    // ── Formula injection ─────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "=1+1",
            "=HYPERLINK(\"http://evil.example\",\"click\")",
            "=cmd|'/c calc'!A1",
            "+1+1",
            "-1+1",
            "@SUM(A1:A9)",
            "\t=1+1",
            "\r=1+1"
    })
    void aValueThatWouldRunAsAFormulaIsDefused(String dangerous) {
        String exported = exportedName(dangerous);

        String unquoted = exported.startsWith("\"") ? exported.substring(1) : exported;
        assertTrue(unquoted.startsWith("'"),
                "should be prefixed so a spreadsheet reads it as text, got: " + exported);
        assertFalse(unquoted.startsWith("="), exported);
        assertFalse(unquoted.startsWith("+"), exported);
        assertFalse(unquoted.startsWith("-"), exported);
        assertFalse(unquoted.startsWith("@"), exported);
    }

    @Test
    void theOriginalTextIsStillReadableAfterBeingDefused() {
        assertEquals("'=1+1", exportedName("=1+1"), "nothing is removed, only prefixed");
    }

    @Test
    void aFormulaContainingACommaIsBothDefusedAndQuoted() {
        assertEquals("\"'=SUM(1,2)\"", exportedName("=SUM(1,2)"));
    }

    // ── Ordinary names are untouched ──────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "Ayaan Sharma",
            "Dr Meera Nair",
            "O'Brien",
            "Jean-Luc Picard",
            "Ram  Kumar",
            "Zoë Müller",
            "A"
    })
    void anOrdinaryNameIsExportedExactlyAsItWas(String name) {
        assertEquals(name, exportedName(name), "an ordinary name must not be altered");
    }

    @Test
    void aHyphenatedNameIsNotMistakenForAFormula() {
        assertEquals("Jean-Luc Picard", exportedName("Jean-Luc Picard"),
                "the hyphen is inside the name, not at the start");
    }

    // ── CSV escaping ──────────────────────────────────────────────────────────────────────

    @Test
    void aNameWithACommaIsQuoted() {
        assertEquals("\"Sharma, Ayaan\"", exportedName("Sharma, Ayaan"));
    }

    @Test
    void aNameWithAQuoteHasItDoubled() {
        assertEquals("\"Ayaan \"\"Ace\"\" Sharma\"", exportedName("Ayaan \"Ace\" Sharma"));
    }

    @Test
    void aNameWithALineBreakIsQuotedSoItCannotSplitTheRow() {
        String exported = exportedName("Ayaan\nSharma");

        assertEquals("\"Ayaan\nSharma\"", exported);
        assertTrue(exported.startsWith("\"") && exported.endsWith("\""),
                "an unquoted newline would break the row into two: " + exported);
        assertEquals(10, seatRow("Ayaan\nSharma").replaceAll("\"[^\"]*\"", "X").split(",").length,
                "the row still holds exactly ten fields despite the newline inside one of them");
    }

    @Test
    void aNameWithACarriageReturnIsQuotedToo() {
        String exported = exportedName("Ayaan\r\nSharma");

        assertTrue(exported.startsWith("\""), exported);
        assertTrue(exported.endsWith("\""), exported);
    }

    @Test
    void theHeaderAndColumnCountAreUnchanged() {
        String csv = SeatingPlanCsv.write(planWithStudentNamed("Ayaan Sharma"));
        List<String> lines = csv.lines().toList();
        assertEquals(2, lines.size(), "an ordinary name takes exactly one line");

        assertEquals("slot_id,paper_code,exam_date,start_time,room_id,room_name,row,seat,"
                + "student_id,student_name", lines.get(0));
        assertEquals(10, lines.get(1).split(",").length);
    }

    @Test
    void aRoomNameThatLooksLikeAFormulaIsDefusedAsWell() {
        Student student = new Student("S1", "Ayaan Sharma", "CSE", Set.of("CS301"));
        ExamData data = new ExamData(
                List.of(student),
                List.of(Room.ofCapacity("R1", "=1+1", 10, "Block A")),
                List.of(new Faculty("F1", "Faculty One", "CSE", false, Set.of())),
                List.of(new Exam("E1", "CS301", "CS301 paper", DAY, LocalTime.of(9, 0), null)));
        SeatingPlan seating = new SeatingPlan();
        seating.place("E1", new Seat("R1", 1, 1, "S1"));
        ExamPlan plan = new ExamPlan(data, ExamMode.MIDSEM,
                List.of(new ExamSlot("E1", "CS301", "CS301 paper", DAY, LocalTime.of(9, 0), 120)),
                seating, new DutyRoster());

        String row = SeatingPlanCsv.write(plan).lines().toList().get(1);

        assertTrue(row.contains(",'=1+1,"), "every untrusted column is defused, not just the name: " + row);
    }

    @Test
    void anEmptyNameIsHandledWithoutFailing() {
        assertEquals("", exportedName(""));
    }
}
