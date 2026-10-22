package edsa.data;

import edsa.core.Assignment;
import edsa.core.Durations;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;
import edsa.core.Faculty;
import edsa.core.PlacedStudent;
import edsa.core.Room;
import edsa.core.RoomSitting;
import edsa.core.Workload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Writes a computed plan out as a workbook the examination office can circulate and pin to a
 * door.
 *
 * <p>Four sheets, each answering one question:
 *
 * <ul>
 *   <li><strong>Timetable</strong> — what is sat when, one row per paper.
 *   <li><strong>Seating Matrix</strong> — where each student sits, drawn as the room's grid,
 *       one block per room per sitting. This is the sheet that gets printed and put up.
 *   <li><strong>Seating</strong> — the same seats as a flat table, filterable, for looking one
 *       student up.
 *   <li><strong>Duties</strong> — who invigilates what.
 * </ul>
 *
 * <p>The exporter does no allocating. Every seat it draws was placed by
 * {@code GreedyAllocator}, and the grid it draws them on is the room's own
 * {@code rowCount × columnCount}; nothing here decides where anybody sits, so formatting
 * cannot change the plan.
 *
 * <p>One honest limitation, stated on the sheet itself: a room imported with only a capacity
 * has no surveyed furniture layout, so the grid is the system's standard addressing — six
 * seats to a row — rather than a picture of the actual room. The coordinates shown are the
 * ones the allocator assigned and the ones the Room schedule page shows, not invented ones.
 */
public final class PlanWorkbook {

    /** Wide enough for an id above a full name, at the 9pt the seat cells use. */
    private static final int SEAT_COLUMN_WIDTH = 24 * 256;
    private static final int ROW_HEADER_WIDTH = 10 * 256;
    private static final short SEAT_ROW_HEIGHT = 34 * 20;

    private PlanWorkbook() {
    }

    public static byte[] write(ExamPlan plan) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            WorkbookStyles styles = new WorkbookStyles(book);

            timetable(book, styles, plan);
            seatingMatrix(book, styles, plan);
            seating(book, styles, plan);
            duties(book, styles, plan);

            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot build the plan workbook", e);
        }
    }

    // ── Timetable ─────────────────────────────────────────────────────────────────────────

    /**
     * One row per paper.
     *
     * <p>Deliberately unchanged in shape — header on the first row, the same ten columns in
     * the same order — because this sheet is the one other things already read.
     */
    private static void timetable(Workbook book, WorkbookStyles styles, ExamPlan plan) {
        Sheet sheet = book.createSheet("Timetable");
        headerRow(sheet, styles, List.of("Exam", "Paper", "Title", "Date", "Start", "End",
                "Duration", "Rooms", "Students", "Invigilators"));

        List<RoomSitting> all = plan.sittings();
        int rowIndex = 1;
        for (ExamSlot slot : plan.schedule()) {
            List<RoomSitting> sittings = all.stream()
                    .filter(sitting -> sitting.slot().getId().equals(slot.getId()))
                    .toList();
            Row row = sheet.createRow(rowIndex++);
            write(row, styles, slot.getId(), slot.getPaperCode(), slot.getPaperName(),
                    slot.getDate().toString(), slot.getStartTime().toString(), slot.getEndTime().toString(),
                    Durations.label(slot.getDurationMinutes()),
                    String.valueOf(sittings.size()),
                    String.valueOf(sittings.stream().mapToInt(RoomSitting::seated).sum()),
                    sittings.stream().flatMap(sitting -> sitting.invigilators().stream())
                            .map(Faculty::getId).distinct().reduce((a, b) -> a + ", " + b).orElse(""));
        }

        widths(sheet, 10, 18 * 256);
        finishTable(sheet, rowIndex - 1, 10);
    }

    // ── Seating matrix ────────────────────────────────────────────────────────────────────

    /**
     * The seating plan drawn as each room's grid, one block per room per sitting.
     *
     * <p>Blocks are grouped by sitting in schedule order, so the blocks for one paper sit
     * together. Each is headed with everything somebody standing at the door needs: the paper,
     * when it runs, which room, who is invigilating, and how full it is.
     */
    private static void seatingMatrix(Workbook book, WorkbookStyles styles, ExamPlan plan) {
        Sheet sheet = book.createSheet("Seating Matrix");
        int widestRoom = 1;
        int rowIndex = 0;

        rowIndex = matrixTitle(sheet, styles, plan, rowIndex);

        Map<String, List<RoomSitting>> bySlot = new LinkedHashMap<>();
        for (RoomSitting sitting : plan.sittings()) {
            bySlot.computeIfAbsent(sitting.slot().getId(), key -> new java.util.ArrayList<>()).add(sitting);
        }

        for (ExamSlot slot : plan.schedule()) {
            for (RoomSitting sitting : bySlot.getOrDefault(slot.getId(), List.of())) {
                widestRoom = Math.max(widestRoom, sitting.room().getColumnCount());
                rowIndex = matrixBlock(sheet, styles, sitting, rowIndex);
            }
        }

        sheet.setColumnWidth(0, ROW_HEADER_WIDTH);
        for (int column = 1; column <= widestRoom; column++) {
            sheet.setColumnWidth(column, SEAT_COLUMN_WIDTH);
        }
        // The title block is three rows; keeping them in view while scrolling a long sheet
        // means the reader never loses which session they are looking at.
        sheet.createFreezePane(1, 4);
        printing(sheet, true, widestRoom + 1);
    }

    private static int matrixTitle(Sheet sheet, WorkbookStyles styles, ExamPlan plan, int rowIndex) {
        int at = rowIndex;
        cell(sheet.createRow(at++), 0, "EXAMINATION SEATING PLAN", styles.title);
        cell(sheet.createRow(at++), 0,
                plan.mode().label() + " · " + plan.mode().durationLabel() + " per paper · "
                        + plan.schedule().size() + " papers · "
                        + plan.seating().seatedCount() + " seats allocated",
                styles.subtitle);
        cell(sheet.createRow(at++), 0,
                "Each block below is one room for one paper. A cell holds the student seated at "
                        + "that position. — is a free seat; a shaded × is a grid position the "
                        + "room does not seat. Rows and seats are numbered from 1.",
                styles.legend);
        sheet.createRow(at++);
        return at;
    }

    /** One room's grid for one sitting, headed by its identifying details. */
    private static int matrixBlock(Sheet sheet, WorkbookStyles styles, RoomSitting sitting, int rowIndex) {
        Room room = sitting.room();
        ExamSlot slot = sitting.slot();
        int columns = Math.max(1, room.getColumnCount());
        int at = rowIndex;

        cell(sheet.createRow(at), 0, slot.getPaperCode() + " — " + slot.getPaperName(),
                styles.sectionTitle);
        merge(sheet, at++, 0, columns);

        cell(sheet.createRow(at), 0, slot.getDate() + " · " + slot.getStartTime() + "–"
                + slot.getEndTime() + " (" + Durations.label(slot.getDurationMinutes()) + ") · Room "
                + room.getId() + " — " + room.getName() + ", " + room.getBuilding(), styles.sectionDetail);
        merge(sheet, at++, 0, columns);

        cell(sheet.createRow(at), 0, "Invigilator: " + invigilatorsOf(sitting)
                + " · Seated " + sitting.seated() + " of " + room.capacity(), styles.sectionDetail);
        merge(sheet, at++, 0, columns);

        // Column headings: the corner cell names what the row labels mean.
        Row headings = sheet.createRow(at++);
        cell(headings, 0, "Row", styles.columnHeader);
        for (int column = 1; column <= columns; column++) {
            cell(headings, column, "Seat " + column, styles.columnHeader);
        }

        Map<String, PlacedStudent> byPosition = new LinkedHashMap<>();
        for (PlacedStudent placed : sitting.students()) {
            byPosition.put(placed.row() + ":" + placed.column(), placed);
        }

        for (int gridRow = 1; gridRow <= Math.max(1, room.getRowCount()); gridRow++) {
            Row row = sheet.createRow(at++);
            row.setHeight(SEAT_ROW_HEIGHT);
            cell(row, 0, String.valueOf(gridRow), styles.rowHeader);
            for (int column = 1; column <= columns; column++) {
                PlacedStudent placed = byPosition.get(gridRow + ":" + column);
                if (placed != null) {
                    cell(row, column, placed.student().getId() + "\n" + placed.student().getName(),
                            styles.seatId);
                } else if (seatExists(room, gridRow, column)) {
                    cell(row, column, "—", styles.emptySeat);
                } else {
                    cell(row, column, "×", styles.unavailableSeat);
                }
            }
        }

        sheet.createRow(at++);
        return at;
    }

    /**
     * Whether the room actually seats that grid position.
     *
     * <p>A room's grid can be wider than its capacity — twenty seats laid out six to a row
     * leaves four positions in the last row that nobody sits in. Counting row by row is the
     * same order the allocator fills them in, so the two always agree.
     */
    private static boolean seatExists(Room room, int gridRow, int column) {
        int columns = Math.max(1, room.getColumnCount());
        int position = (gridRow - 1) * columns + column;
        return position <= room.capacity();
    }

    private static String invigilatorsOf(RoomSitting sitting) {
        return sitting.invigilators().stream()
                .map(member -> member.getName() + " (" + member.getId() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("none assigned");
    }

    // ── Seating list ──────────────────────────────────────────────────────────────────────

    /** Every seat as one row, so a single student can be found with a filter. */
    private static void seating(Workbook book, WorkbookStyles styles, ExamPlan plan) {
        Sheet sheet = book.createSheet("Seating");
        headerRow(sheet, styles, List.of("Exam", "Paper", "Date", "Start", "End", "Room", "Building",
                "Row", "Seat", "Student ID", "Student"));
        int rowIndex = 1;
        for (RoomSitting sitting : plan.sittings()) {
            for (PlacedStudent placed : sitting.students()) {
                Row row = sheet.createRow(rowIndex++);
                write(row, styles, sitting.slot().getId(), sitting.slot().getPaperCode(),
                        sitting.slot().getDate().toString(), sitting.slot().getStartTime().toString(),
                        sitting.slot().getEndTime().toString(), sitting.room().getId(),
                        sitting.room().getBuilding(), String.valueOf(placed.row()),
                        String.valueOf(placed.column()), placed.student().getId(), placed.student().getName());
            }
        }
        widths(sheet, 11, 16 * 256);
        finishTable(sheet, rowIndex - 1, 11);
    }

    // ── Duties ────────────────────────────────────────────────────────────────────────────

    private static void duties(Workbook book, WorkbookStyles styles, ExamPlan plan) {
        Sheet sheet = book.createSheet("Duties");
        headerRow(sheet, styles, List.of("Faculty ID", "Name", "Department", "Duties", "Exam", "Paper",
                "Date", "Start", "End", "Room"));
        int rowIndex = 1;
        for (Workload workload : plan.workloads()) {
            if (workload.duties() == 0) {
                Row row = sheet.createRow(rowIndex++);
                write(row, styles, workload.member().getId(), workload.member().getName(),
                        workload.member().getDepartment(), "0", "", "", "", "", "", "");
                continue;
            }
            for (ExamSlot slot : workload.slots()) {
                String room = plan.duties().forFaculty(workload.member().getId()).stream()
                        .filter(duty -> duty.slotId().equals(slot.getId()))
                        .map(Assignment::roomId).findFirst().orElse("");
                Row row = sheet.createRow(rowIndex++);
                write(row, styles, workload.member().getId(), workload.member().getName(),
                        workload.member().getDepartment(), String.valueOf(workload.duties()),
                        slot.getId(), slot.getPaperCode(), slot.getDate().toString(),
                        slot.getStartTime().toString(), slot.getEndTime().toString(), room);
            }
        }
        widths(sheet, 10, 18 * 256);
        finishTable(sheet, rowIndex - 1, 10);
    }

    // ── Shared plumbing ───────────────────────────────────────────────────────────────────

    private static void headerRow(Sheet sheet, WorkbookStyles styles, List<String> columns) {
        Row row = sheet.createRow(0);
        row.setHeight((short) (22 * 20));
        for (int index = 0; index < columns.size(); index++) {
            cell(row, index, columns.get(index), styles.columnHeader);
        }
    }

    private static void write(Row row, WorkbookStyles styles, String... values) {
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            boolean numeric = !value.isEmpty() && value.chars().allMatch(Character::isDigit);
            cell(row, index, value, numeric ? styles.dataNumeric : styles.data);
        }
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void merge(Sheet sheet, int rowIndex, int firstColumn, int lastColumn) {
        if (lastColumn > firstColumn) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, firstColumn, lastColumn));
        }
    }

    private static void widths(Sheet sheet, int columns, int width) {
        for (int index = 0; index < columns; index++) {
            sheet.setColumnWidth(index, width);
        }
    }

    /** Freeze the heading, let it be filtered, and make it print sensibly. */
    private static void finishTable(Sheet sheet, int lastDataRow, int columns) {
        sheet.createFreezePane(0, 1);
        if (lastDataRow >= 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, lastDataRow, 0, columns - 1));
        }
        sheet.setRepeatingRows(CellRangeAddress.valueOf("1:1"));
        printing(sheet, true, columns);
    }

    /** A4, fitted to the page width, with margins a printer will not clip. */
    private static void printing(Sheet sheet, boolean landscape, int columns) {
        sheet.setFitToPage(true);
        sheet.setMargin(Sheet.LeftMargin, 0.4);
        sheet.setMargin(Sheet.RightMargin, 0.4);
        sheet.setMargin(Sheet.TopMargin, 0.5);
        sheet.setMargin(Sheet.BottomMargin, 0.5);

        PrintSetup setup = sheet.getPrintSetup();
        setup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        setup.setLandscape(landscape || columns > 8);
        setup.setFitWidth((short) 1);
        setup.setFitHeight((short) 0);
        sheet.setPrintGridlines(false);
        sheet.getFooter().setRight("Page &P of &N");
    }
}
