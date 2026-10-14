package edsa.data;

import edsa.core.Assignment;
import edsa.core.Durations;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;
import edsa.core.Faculty;
import edsa.core.PlacedStudent;
import edsa.core.RoomSitting;
import edsa.core.Workload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Writes a computed plan out as a workbook the examination office can circulate: the
 * timetable, the seating, and who is on duty, one sheet each.
 */
public final class PlanWorkbook {

    private PlanWorkbook() {
    }

    public static byte[] write(ExamPlan plan) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(book);

            timetable(book, header, plan);
            seating(book, header, plan);
            duties(book, header, plan);

            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot build the plan workbook", e);
        }
    }

    private static void timetable(Workbook book, CellStyle header, ExamPlan plan) {
        Sheet sheet = book.createSheet("Timetable");
        headerRow(sheet, header, List.of("Exam", "Paper", "Title", "Date", "Start", "End",
                "Duration", "Rooms", "Students", "Invigilators"));
        int rowIndex = 1;
        for (ExamSlot slot : plan.schedule()) {
            List<RoomSitting> sittings = plan.sittings().stream()
                    .filter(sitting -> sitting.slot().getId().equals(slot.getId()))
                    .toList();
            Row row = sheet.createRow(rowIndex++);
            write(row, 0, slot.getId(), slot.getPaperCode(), slot.getPaperName(),
                    slot.getDate().toString(), slot.getStartTime().toString(), slot.getEndTime().toString(),
                    Durations.label(slot.getDurationMinutes()),
                    String.valueOf(sittings.size()),
                    String.valueOf(sittings.stream().mapToInt(RoomSitting::seated).sum()),
                    sittings.stream().flatMap(sitting -> sitting.invigilators().stream())
                            .map(Faculty::getId).distinct().reduce((a, b) -> a + ", " + b).orElse(""));
        }
        widths(sheet, 10);
        sheet.createFreezePane(0, 1);
    }

    private static void seating(Workbook book, CellStyle header, ExamPlan plan) {
        Sheet sheet = book.createSheet("Seating");
        headerRow(sheet, header, List.of("Exam", "Paper", "Date", "Start", "End", "Room", "Building",
                "Row", "Seat", "Student ID", "Student"));
        int rowIndex = 1;
        for (RoomSitting sitting : plan.sittings()) {
            for (PlacedStudent placed : sitting.students()) {
                Row row = sheet.createRow(rowIndex++);
                write(row, 0, sitting.slot().getId(), sitting.slot().getPaperCode(),
                        sitting.slot().getDate().toString(), sitting.slot().getStartTime().toString(),
                        sitting.slot().getEndTime().toString(), sitting.room().getId(),
                        sitting.room().getBuilding(), String.valueOf(placed.row()),
                        String.valueOf(placed.column()), placed.student().getId(), placed.student().getName());
            }
        }
        widths(sheet, 11);
        sheet.createFreezePane(0, 1);
    }

    private static void duties(Workbook book, CellStyle header, ExamPlan plan) {
        Sheet sheet = book.createSheet("Duties");
        headerRow(sheet, header, List.of("Faculty ID", "Name", "Department", "Duties", "Exam", "Paper",
                "Date", "Start", "End", "Room"));
        int rowIndex = 1;
        for (Workload workload : plan.workloads()) {
            if (workload.duties() == 0) {
                Row row = sheet.createRow(rowIndex++);
                write(row, 0, workload.member().getId(), workload.member().getName(),
                        workload.member().getDepartment(), "0", "", "", "", "", "", "");
                continue;
            }
            for (ExamSlot slot : workload.slots()) {
                String room = plan.duties().forFaculty(workload.member().getId()).stream()
                        .filter(duty -> duty.slotId().equals(slot.getId()))
                        .map(Assignment::roomId).findFirst().orElse("");
                Row row = sheet.createRow(rowIndex++);
                write(row, 0, workload.member().getId(), workload.member().getName(),
                        workload.member().getDepartment(), String.valueOf(workload.duties()),
                        slot.getId(), slot.getPaperCode(), slot.getDate().toString(),
                        slot.getStartTime().toString(), slot.getEndTime().toString(), room);
            }
        }
        widths(sheet, 10);
        sheet.createFreezePane(0, 1);
    }

    private static void headerRow(Sheet sheet, CellStyle style, List<String> columns) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < columns.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(columns.get(index));
            cell.setCellStyle(style);
        }
    }

    private static void write(Row row, int from, String... values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(from + index).setCellValue(values[index]);
        }
    }

    private static void widths(Sheet sheet, int columns) {
        for (int index = 0; index < columns; index++) {
            sheet.setColumnWidth(index, 18 * 256);
        }
    }

    private static CellStyle headerStyle(Workbook book) {
        Font font = book.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = book.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
