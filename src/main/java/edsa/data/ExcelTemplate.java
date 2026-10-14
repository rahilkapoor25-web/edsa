package edsa.data;

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
 * Writes the workbook the administrator fills in: every required worksheet, labelled columns,
 * a few clearly fictional example rows and a sheet explaining the formats.
 *
 * <p>Replacing the example rows with real records is all that is needed to make it importable.
 */
public final class ExcelTemplate {

    private ExcelTemplate() {
    }

    public static byte[] write() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);

            instructions(workbook);

            sheet(workbook, ExcelWorkbookReader.STUDENTS, header,
                    List.of("Student ID", "Name", "Email", "Department", "Year", "Section"),
                    List.of(
                            List.of("S001", "Example Student One", "s001@example.edu", "CSE", "3", "A"),
                            List.of("S002", "Example Student Two", "s002@example.edu", "CSE", "3", "A"),
                            List.of("S003", "Example Student Three", "s003@example.edu", "ECE", "3", "B")));

            sheet(workbook, ExcelWorkbookReader.FACULTY, header,
                    List.of("Faculty ID", "Name", "Email", "Department", "Available"),
                    List.of(
                            List.of("F01", "Example Faculty One", "f01@example.edu", "CSE", "yes"),
                            List.of("F02", "Example Faculty Two", "f02@example.edu", "ECE", "yes"),
                            List.of("F03", "Example Faculty Three", "f03@example.edu", "ME", "no")));

            sheet(workbook, ExcelWorkbookReader.ROOMS, header,
                    List.of("Room ID", "Name", "Building", "Capacity", "Available"),
                    List.of(
                            List.of("R101", "Lecture Hall 1", "Block A", "30", "yes"),
                            List.of("R102", "Lecture Hall 2", "Block A", "24", "yes"),
                            List.of("R201", "Seminar Hall", "Block B", "20", "yes")));

            sheet(workbook, ExcelWorkbookReader.SUBJECTS, header,
                    List.of("Subject Code", "Subject Name", "Department", "Semester"),
                    List.of(
                            List.of("CS301", "Data Structures", "CSE", "5"),
                            List.of("CS302", "Operating Systems", "CSE", "5"),
                            List.of("EC301", "Signals and Systems", "ECE", "5")));

            sheet(workbook, ExcelWorkbookReader.ENROLLMENTS, header,
                    List.of("Student ID", "Subject Code"),
                    List.of(
                            List.of("S001", "CS301"),
                            List.of("S001", "CS302"),
                            List.of("S002", "CS301"),
                            List.of("S003", "EC301")));

            sheet(workbook, ExcelWorkbookReader.EXAMS, header,
                    List.of("Exam ID", "Subject Code", "Date", "Start Time", "Duration Minutes"),
                    List.of(
                            List.of("E1", "CS301", "2026-11-10", "09:00", ""),
                            List.of("E2", "CS302", "2026-11-11", "09:00", ""),
                            List.of("E3", "EC301", "2026-11-10", "", "")));

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot build the template workbook", e);
        }
    }

    private static void instructions(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Read me");
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle title = workbook.createCellStyle();
        title.setFont(bold);

        List<String> lines = List.of(
                "EDSA import workbook",
                "",
                "Replace the example rows on each worksheet with your own records, then upload this one file.",
                "The example rows are invented for illustration; they are not real people, rooms or papers.",
                "",
                "Students     Student ID, Name and Department are required. Email, Year and Section are optional.",
                "Faculty      Faculty ID, Name and Department are required. Available accepts yes or no;",
                "             blank counts as yes. An unavailable member is imported but never put on duty.",
                "Rooms        Room ID, Building and Capacity are required. Capacity is a whole number of seats",
                "             and decides how many students the room may hold. Available accepts yes or no.",
                "Subjects     Subject Code, Subject Name and Department are required. Semester is optional.",
                "Enrollments  One row per student per paper. Both ids must exist on their own worksheets.",
                "Exams        Exam ID, Subject Code and Date are required. Date is yyyy-mm-dd.",
                "             Start Time (hh:mm) fixes when the paper starts; leave it blank to let the",
                "             scheduler choose the earliest time that works.",
                "             Duration Minutes overrides the exam mode for that paper; leave it blank to use",
                "             the selected mode (Quiz 10, Midsem 120, Endsem 180 minutes).",
                "",
                "Do not add formulas: values are read as text and formulas are refused.",
                "Worksheet names must stay exactly as they are.");

        for (int index = 0; index < lines.size(); index++) {
            Cell cell = sheet.createRow(index).createCell(0);
            cell.setCellValue(lines.get(index));
            if (index == 0) {
                cell.setCellStyle(title);
            }
        }
        sheet.setColumnWidth(0, 110 * 256);
    }

    private static void sheet(Workbook workbook, String name, CellStyle headerStyle,
                              List<String> columns, List<List<String>> examples) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        for (int index = 0; index < columns.size(); index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(columns.get(index));
            cell.setCellStyle(headerStyle);
        }
        for (int rowIndex = 0; rowIndex < examples.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<String> values = examples.get(rowIndex);
            for (int index = 0; index < values.size(); index++) {
                row.createCell(index).setCellValue(values.get(index));
            }
        }
        for (int index = 0; index < columns.size(); index++) {
            sheet.setColumnWidth(index, 22 * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private static CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
