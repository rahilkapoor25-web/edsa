package edsa.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.ExamPlan;
import edsa.core.ExamPlanner;
import edsa.core.PlacedStudent;
import edsa.core.RoomSitting;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The downloaded workbook, read back and checked against the plan it came from.
 *
 * <p>The point of all of this is that formatting a plan must never change it. Every seat the
 * allocator placed is looked up in the matrix by its own coordinates, and the total number of
 * occupied cells is compared with the plan's own count — so a seat that went missing, moved,
 * or got drawn twice fails a test rather than quietly going out on a printed sheet.
 */
class SeatingMatrixTest {

    private static ExamPlan plan;
    private static byte[] workbook;

    @BeforeAll
    static void exportTheBundledSamplePlan() {
        ExamData data = SampleData.load();
        plan = new ExamPlanner().plan(data, ExamMode.ENDSEM).plan();
        workbook = PlanWorkbook.write(plan);
    }

    private static Workbook open() {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(workbook));
        } catch (Exception e) {
            throw new IllegalStateException("the generated workbook could not be reopened", e);
        }
    }

    private static String text(Row row, int column) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(column);
        return cell == null ? "" : cell.getStringCellValue();
    }

    /** One room's grid as it was drawn, addressed [gridRow][seatColumn], both from 1. */
    private record Block(String paperCode, String roomId, Map<String, String> cells, int rows, int columns) {
        String at(int gridRow, int column) {
            return cells.getOrDefault(gridRow + ":" + column, "");
        }
    }

    /**
     * Parses the matrix sheet back into blocks.
     *
     * <p>A block is found by its heading row — the one whose first cell reads "Row" — with the
     * paper on the row three above it and the room on the row two above.
     */
    private static Map<String, Block> blocks() {
        Map<String, Block> found = new LinkedHashMap<>();
        try (Workbook book = open()) {
            Sheet sheet = book.getSheet("Seating Matrix");
            assertNotNull(sheet, "the workbook should have a Seating Matrix sheet");

            for (int index = 0; index <= sheet.getLastRowNum(); index++) {
                if (!"Row".equals(text(sheet.getRow(index), 0))) {
                    continue;
                }
                Row headings = sheet.getRow(index);
                int columns = 0;
                while (text(headings, columns + 1).startsWith("Seat ")) {
                    columns++;
                }

                String paperCode = text(sheet.getRow(index - 3), 0).split(" — ")[0];
                String detail = text(sheet.getRow(index - 2), 0);
                String roomId = detail.substring(detail.indexOf("Room ") + 5).split(" ")[0];

                Map<String, String> cells = new LinkedHashMap<>();
                int gridRows = 0;
                for (int at = index + 1; at <= sheet.getLastRowNum(); at++) {
                    String label = text(sheet.getRow(at), 0);
                    if (label.isEmpty()) {
                        break;
                    }
                    gridRows++;
                    for (int column = 1; column <= columns; column++) {
                        cells.put(label + ":" + column, text(sheet.getRow(at), column));
                    }
                }
                found.put(paperCode + "/" + roomId, new Block(paperCode, roomId, cells, gridRows, columns));
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not parse the matrix sheet", e);
        }
        return found;
    }

    // ── It is a workbook, with the sheets it should have ─────────────────────────────────

    @Test
    void theWorkbookIsGeneratedAndCanBeReopened() {
        assertTrue(workbook.length > 0);
        try (Workbook book = open()) {
            assertEquals(4, book.getNumberOfSheets());
        } catch (Exception e) {
            throw new AssertionError("the generated workbook is not readable", e);
        }
    }

    @Test
    void everySheetIsPresentAndNamedAsExpected() {
        try (Workbook book = open()) {
            assertNotNull(book.getSheet("Timetable"));
            assertNotNull(book.getSheet("Seating Matrix"));
            assertNotNull(book.getSheet("Seating"));
            assertNotNull(book.getSheet("Duties"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theMatrixSheetIsTitledAndExplainsItsSymbols() {
        try (Workbook book = open()) {
            Sheet sheet = book.getSheet("Seating Matrix");
            assertEquals("EXAMINATION SEATING PLAN", text(sheet.getRow(0), 0));
            assertTrue(text(sheet.getRow(1), 0).contains("Endsem"), text(sheet.getRow(1), 0));
            String legend = text(sheet.getRow(2), 0);
            assertTrue(legend.contains("— is a free seat"), legend);
            assertTrue(legend.contains("×"), legend);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ── The data is exactly the plan's ───────────────────────────────────────────────────

    @Test
    void thereIsOneBlockPerRoomSitting() {
        assertEquals(plan.sittings().size(), blocks().size(),
                "every room that seats anybody should be drawn exactly once");
    }

    @Test
    void everyStudentIsDrawnAtTheSeatTheAllocatorGaveThem() {
        Map<String, Block> drawn = blocks();

        for (RoomSitting sitting : plan.sittings()) {
            Block block = drawn.get(sitting.slot().getPaperCode() + "/" + sitting.room().getId());
            assertNotNull(block, "no block for " + sitting.slot().getPaperCode()
                    + " in " + sitting.room().getId());

            for (PlacedStudent placed : sitting.students()) {
                String cell = block.at(placed.row(), placed.column());
                assertTrue(cell.contains(placed.student().getId()),
                        "seat " + placed.row() + "/" + placed.column() + " of " + block.roomId()
                                + " should hold " + placed.student().getId() + " but held '" + cell + "'");
                assertTrue(cell.contains(placed.student().getName()),
                        "the name should be in the cell too, got '" + cell + "'");
            }
        }
    }

    @Test
    void noStudentIsOmittedAndNoneIsDrawnTwice() {
        List<String> occupied = new ArrayList<>();
        for (Map.Entry<String, Block> entry : blocks().entrySet()) {
            for (String cell : entry.getValue().cells().values()) {
                if (!cell.isEmpty() && !cell.equals("—") && !cell.equals("×")) {
                    // Keyed by sitting as well as student: one student legitimately sits in
                    // the same room again for a different paper.
                    occupied.add(entry.getKey() + "/" + cell);
                }
            }
        }

        assertEquals(plan.seating().seatedCount(), occupied.size(),
                "the matrix should hold exactly as many students as the plan seated");
        assertEquals(occupied.size(), new HashSet<>(occupied).size(),
                "no student is drawn twice in the same sitting");
    }

    @Test
    void everySeatInThePlanAppearsInTheFlatSeatingSheetToo() {
        try (Workbook book = open()) {
            Sheet sheet = book.getSheet("Seating");
            assertEquals(plan.seating().seatedCount(), sheet.getLastRowNum(),
                    "one row per seat under the header");

            Set<String> rows = new HashSet<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                rows.add(text(row, 0) + "/" + text(row, 5) + "/" + text(row, 7)
                        + "/" + text(row, 8) + "/" + text(row, 9));
            }
            for (RoomSitting sitting : plan.sittings()) {
                for (PlacedStudent placed : sitting.students()) {
                    String key = sitting.slot().getId() + "/" + sitting.room().getId() + "/"
                            + placed.row() + "/" + placed.column() + "/" + placed.student().getId();
                    assertTrue(rows.contains(key), "missing from the Seating sheet: " + key);
                }
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aFreeSeatAndAnUnavailablePositionAreToldApart() {
        Map<String, Block> drawn = blocks();

        boolean sawFreeOrUnavailable = false;
        for (RoomSitting sitting : plan.sittings()) {
            Block block = drawn.get(sitting.slot().getPaperCode() + "/" + sitting.room().getId());
            int capacity = sitting.room().capacity();
            int columns = block.columns();

            for (int gridRow = 1; gridRow <= block.rows(); gridRow++) {
                for (int column = 1; column <= columns; column++) {
                    final int atRow = gridRow;
                    final int atColumn = column;
                    boolean taken = sitting.students().stream()
                            .anyMatch(placed -> placed.row() == atRow && placed.column() == atColumn);
                    if (taken) {
                        continue;
                    }
                    String cell = block.at(gridRow, column);
                    boolean withinCapacity = (gridRow - 1) * columns + column <= capacity;
                    assertEquals(withinCapacity ? "—" : "×", cell,
                            "position " + gridRow + "/" + column + " of " + block.roomId()
                                    + " (capacity " + capacity + ")");
                    sawFreeOrUnavailable = true;
                }
            }
        }
        assertTrue(sawFreeOrUnavailable, "the sample plan should leave at least one seat unfilled");
    }

    @Test
    void theRoomDetailsAreOnEveryBlock() {
        try (Workbook book = open()) {
            Sheet sheet = book.getSheet("Seating Matrix");
            int headings = 0;
            for (int index = 0; index <= sheet.getLastRowNum(); index++) {
                if (!"Row".equals(text(sheet.getRow(index), 0))) {
                    continue;
                }
                headings++;
                String detail = text(sheet.getRow(index - 2), 0);
                String staffing = text(sheet.getRow(index - 1), 0);
                assertTrue(detail.contains("Room "), detail);
                assertTrue(detail.matches(".*\\d{4}-\\d{2}-\\d{2}.*"), "the date should be shown: " + detail);
                assertTrue(staffing.startsWith("Invigilator:"), staffing);
                assertTrue(staffing.contains("Seated "), staffing);
            }
            assertEquals(plan.sittings().size(), headings);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ── The existing sheets are unchanged in shape ───────────────────────────────────────

    @Test
    void theTimetableSheetKeepsItsColumnsInTheSameOrder() {
        try (Workbook book = open()) {
            Row header = book.getSheet("Timetable").getRow(0);
            assertEquals(List.of("Exam", "Paper", "Title", "Date", "Start", "End",
                            "Duration", "Rooms", "Students", "Invigilators"),
                    List.of(text(header, 0), text(header, 1), text(header, 2), text(header, 3),
                            text(header, 4), text(header, 5), text(header, 6), text(header, 7),
                            text(header, 8), text(header, 9)));
            assertEquals(plan.schedule().size(), book.getSheet("Timetable").getLastRowNum());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theSeatingAndDutiesHeadingsAreUnchanged() {
        try (Workbook book = open()) {
            assertEquals("Student", text(book.getSheet("Seating").getRow(0), 10));
            assertEquals("Exam", text(book.getSheet("Seating").getRow(0), 0));
            assertEquals("Faculty ID", text(book.getSheet("Duties").getRow(0), 0));
            assertEquals("Room", text(book.getSheet("Duties").getRow(0), 9));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ── Formatting that can be checked rather than eyeballed ────────────────────────────

    @Test
    void theTabularSheetsFreezeTheirHeadingAndCanBeFiltered() {
        try (Workbook book = open()) {
            for (String name : List.of("Timetable", "Seating", "Duties")) {
                Sheet sheet = book.getSheet(name);
                assertNotNull(sheet.getPaneInformation(), name + " should freeze its heading");
                assertTrue(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet)
                                .getCTWorksheet().isSetAutoFilter(),
                        name + " should be filterable");
                assertNotNull(sheet.getRepeatingRows(), name + " should repeat its heading when printed");
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void everySheetIsSetUpToPrintOnA4FittedToTheWidth() {
        try (Workbook book = open()) {
            for (int index = 0; index < book.getNumberOfSheets(); index++) {
                Sheet sheet = book.getSheetAt(index);
                assertTrue(sheet.getFitToPage(), sheet.getSheetName() + " should fit to page");
                assertEquals(1, sheet.getPrintSetup().getFitWidth(), sheet.getSheetName());
                assertEquals(PrintSetupPaper.A4, sheet.getPrintSetup().getPaperSize(),
                        sheet.getSheetName());
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** A4 is 9 in POI's paper-size table; naming it keeps the assertion readable. */
    private static final class PrintSetupPaper {
        static final short A4 = org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE;
    }

    @Test
    void mergedCellsAreUsedOnlyForHeadingsAndNeverOnAStudentCell() {
        try (Workbook book = open()) {
            Sheet matrix = book.getSheet("Seating Matrix");
            assertTrue(matrix.getNumMergedRegions() > 0, "section headings are merged");

            for (int index = 0; index < matrix.getNumMergedRegions(); index++) {
                var region = matrix.getMergedRegion(index);
                assertEquals(region.getFirstRow(), region.getLastRow(),
                        "a merge should never span rows");
                assertEquals(0, region.getFirstColumn(), "merges start at the heading column");
                assertFalse("Row".equals(text(matrix.getRow(region.getFirstRow()), 0)),
                        "a grid heading row must not be merged");
            }

            for (String name : List.of("Timetable", "Seating", "Duties")) {
                assertEquals(0, book.getSheet(name).getNumMergedRegions(),
                        name + " is a plain table, so nothing in it is merged");
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theSeatCellsWrapSoALongNameStaysReadable() {
        try (Workbook book = open()) {
            Sheet sheet = book.getSheet("Seating Matrix");
            for (int index = 0; index <= sheet.getLastRowNum(); index++) {
                if ("Row".equals(text(sheet.getRow(index), 0))) {
                    Cell firstSeat = sheet.getRow(index + 1).getCell(1);
                    assertTrue(firstSeat.getCellStyle().getWrapText(),
                            "a seat cell holds an id and a name on two lines, so it must wrap");
                    assertTrue(sheet.getColumnWidth(1) > 15 * 256, "and be wide enough to read");
                    return;
                }
            }
            throw new AssertionError("no grid was found on the matrix sheet");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * A room imported with only a capacity gets a grid six seats wide, which can be wider
     * than the number of seats it actually has — twenty seats leave four dead positions in
     * the last row. Those must be told apart from seats that are merely free, which is what
     * the shaded cross is for.
     *
     * <p>The bundled CSV rooms are described as rows by columns and so divide evenly, which
     * is why this case needs a plan of its own.
     */
    @Test
    void aGridPositionTheRoomDoesNotSeatIsMarkedAsUnavailable() {
        edsa.core.ExamData data = new edsa.core.ExamData(
                java.util.stream.IntStream.rangeClosed(1, 8)
                        .mapToObj(index -> new edsa.core.Student("S" + index, "Student " + index, "CSE",
                                java.util.Set.of("CS301")))
                        .map(student -> (edsa.core.Student) student)
                        .toList(),
                List.of(edsa.core.Room.ofCapacity("R20", "Small Hall", 20, "Block A")),
                List.of(new edsa.core.Faculty("F1", "Faculty One", "CSE", false, java.util.Set.of())),
                List.of(new edsa.core.Exam("E1", "CS301", "Data Structures",
                        java.time.LocalDate.of(2026, 11, 10), java.time.LocalTime.of(9, 0), null)));

        ExamPlan small = new ExamPlanner().plan(data, ExamMode.MIDSEM).plan();
        byte[] bytes = PlanWorkbook.write(small);

        int crosses = 0;
        int dashes = 0;
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = book.getSheet("Seating Matrix");
            for (int index = 0; index <= sheet.getLastRowNum(); index++) {
                for (int column = 1; column <= 6; column++) {
                    String value = text(sheet.getRow(index), column);
                    if (value.equals("×")) {
                        crosses++;
                    } else if (value.equals("—")) {
                        dashes++;
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        // 6 x 4 grid = 24 positions; the room seats 20, so 4 are unavailable and
        // 20 - 8 seated = 12 are free.
        assertEquals(4, crosses, "the four positions beyond the room's twenty seats");
        assertEquals(12, dashes, "the free seats among the twenty");
    }
}
