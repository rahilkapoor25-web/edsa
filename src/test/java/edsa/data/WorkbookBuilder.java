package edsa.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Builds small workbooks in memory so import tests never touch a real file. */
final class WorkbookBuilder {

    private final Map<String, List<List<String>>> sheets = new LinkedHashMap<>();

    static WorkbookBuilder valid() {
        return new WorkbookBuilder()
                .sheet("Students", List.of("Student ID", "Name", "Email", "Department", "Year", "Section"),
                        List.of(List.of("S001", "Example One", "s1@example.edu", "CSE", "3", "A"),
                                List.of("S002", "Example Two", "s2@example.edu", "CSE", "3", "A"),
                                List.of("S003", "Example Three", "s3@example.edu", "ECE", "3", "B")))
                .sheet("Faculty", List.of("Faculty ID", "Name", "Email", "Department", "Available"),
                        List.of(List.of("F01", "Faculty One", "f1@example.edu", "CSE", "yes"),
                                List.of("F02", "Faculty Two", "f2@example.edu", "ECE", "yes"),
                                List.of("F03", "Faculty Three", "f3@example.edu", "ME", "no")))
                .sheet("Rooms", List.of("Room ID", "Name", "Building", "Capacity", "Available"),
                        List.of(List.of("R101", "Hall One", "Block A", "30", "yes"),
                                List.of("R102", "Hall Two", "Block A", "20", "yes")))
                .sheet("Subjects", List.of("Subject Code", "Subject Name", "Department", "Semester"),
                        List.of(List.of("CS301", "Data Structures", "CSE", "5"),
                                List.of("EC301", "Signals", "ECE", "5")))
                .sheet("Enrollments", List.of("Student ID", "Subject Code"),
                        List.of(List.of("S001", "CS301"), List.of("S002", "CS301"), List.of("S003", "EC301")))
                .sheet("Exams", List.of("Exam ID", "Subject Code", "Date", "Start Time", "Duration Minutes"),
                        List.of(List.of("E1", "CS301", "2026-11-10", "09:00", ""),
                                List.of("E2", "EC301", "2026-11-10", "09:00", "")));
    }

    WorkbookBuilder sheet(String name, List<String> headers, List<List<String>> rows) {
        List<List<String>> content = new ArrayList<>();
        content.add(headers);
        content.addAll(rows);
        sheets.put(name, content);
        return this;
    }

    WorkbookBuilder without(String sheetName) {
        sheets.remove(sheetName);
        return this;
    }

    /** Replaces one sheet's rows, keeping its position. */
    WorkbookBuilder replace(String sheetName, List<String> headers, List<List<String>> rows) {
        return sheet(sheetName, headers, rows);
    }

    byte[] build() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            sheets.forEach((name, content) -> {
                Sheet sheet = workbook.createSheet(name);
                for (int rowIndex = 0; rowIndex < content.size(); rowIndex++) {
                    Row row = sheet.createRow(rowIndex);
                    List<String> values = content.get(rowIndex);
                    for (int column = 0; column < values.size(); column++) {
                        row.createCell(column).setCellValue(values.get(column));
                    }
                }
            });
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
