package edsa.data;

import edsa.core.Exam;
import edsa.core.ExamMode;
import edsa.core.Faculty;
import edsa.core.Room;
import edsa.core.Student;
import edsa.core.Subject;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Reads one workbook holding every input the planner needs, one worksheet per kind.
 *
 * <p>Every value is treated as untrusted text: formulas are refused rather than evaluated,
 * nothing is executed, and a cell that cannot be understood is reported with its worksheet,
 * row and column instead of being skipped. Parsing never touches the database — the caller
 * decides whether to store what came back.
 */
public final class ExcelWorkbookReader {

    public static final String STUDENTS = "Students";
    public static final String FACULTY = "Faculty";
    public static final String ROOMS = "Rooms";
    public static final String SUBJECTS = "Subjects";
    public static final String ENROLLMENTS = "Enrollments";
    public static final String EXAMS = "Exams";

    public static final List<String> REQUIRED_SHEETS = List.of(STUDENTS, FACULTY, ROOMS, SUBJECTS, ENROLLMENTS, EXAMS);

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.of(
            STUDENTS, List.of("Student ID", "Name", "Department"),
            FACULTY, List.of("Faculty ID", "Name", "Department"),
            ROOMS, List.of("Room ID", "Building", "Capacity"),
            SUBJECTS, List.of("Subject Code", "Subject Name", "Department"),
            ENROLLMENTS, List.of("Student ID", "Subject Code"),
            EXAMS, List.of("Exam ID", "Subject Code", "Date"));

    /** Permissive but structural: a local part, an @, a dotted domain. */
    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    /** A guard against a small file that expands into an enormous one. */
    private static final double MAX_COMPRESSION_RATIO = 0.01;

    /**
     * Hard ceilings on what a workbook may expand into, applied while it is being unzipped
     * rather than afterwards.
     *
     * <p>The compression-ratio guard alone is not enough: it permits a hundredfold expansion,
     * so a twelve megabyte upload could still inflate into something over a gigabyte and
     * exhaust the application's memory before a single row had been looked at. These caps
     * stop the unzip instead, which is the only point at which it can still be stopped
     * cheaply. They are far above any real examination session and far below anything that
     * would hurt.
     */
    private static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long MAX_TEXT_BYTES = 32L * 1024 * 1024;
    private static final long MAX_PARTS = 1_000;

    /**
     * The most rows one worksheet may hold.
     *
     * <p>Enrollments is the biggest sheet in practice — one row per student per paper — and
     * fifty thousand covers a session several times larger than the office runs. A file that
     * is within every byte limit but still absurd is refused with a message that says which
     * worksheet, rather than being allowed to fill memory.
     */
    public static final int MAX_ROWS_PER_SHEET = 50_000;

    private final List<String> errors = new ArrayList<>();

    /**
     * Parses a workbook.
     *
     * @return the records it held, or empty when anything was wrong; call {@link #errors()}
     */
    public java.util.Optional<ImportedWorkbook> read(InputStream source) {
        errors.clear();
        ZipSecureFile.setMinInflateRatio(MAX_COMPRESSION_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_BYTES);
        ZipSecureFile.setMaxTextSize(MAX_TEXT_BYTES);
        ZipSecureFile.setMaxFileCount(MAX_PARTS);

        try (Workbook workbook = new XSSFWorkbook(source)) {
            for (String required : REQUIRED_SHEETS) {
                if (workbook.getSheet(required) == null) {
                    errors.add("The workbook has no worksheet named '" + required + "'");
                }
            }
            if (!errors.isEmpty()) {
                return java.util.Optional.empty();
            }

            for (String required : REQUIRED_SHEETS) {
                int rows = workbook.getSheet(required).getLastRowNum() + 1;
                if (rows > MAX_ROWS_PER_SHEET) {
                    errors.add("Worksheet '" + required + "' has " + rows + " rows, which is more than "
                            + "the " + MAX_ROWS_PER_SHEET + " this import accepts. Split the session up "
                            + "or check for stray rows below the data.");
                }
            }
            if (!errors.isEmpty()) {
                return java.util.Optional.empty();
            }

            Map<String, Student> students = readStudents(workbook.getSheet(STUDENTS));
            List<Faculty> faculty = new ArrayList<>();
            Map<String, Boolean> facultyAvailable = new LinkedHashMap<>();
            Map<String, String> facultyEmails = new LinkedHashMap<>();
            readFaculty(workbook.getSheet(FACULTY), faculty, facultyAvailable, facultyEmails);
            List<Room> rooms = new ArrayList<>();
            Map<String, Boolean> roomAvailable = new LinkedHashMap<>();
            readRooms(workbook.getSheet(ROOMS), rooms, roomAvailable);
            Map<String, Subject> subjects = readSubjects(workbook.getSheet(SUBJECTS));
            Map<String, List<String>> enrollments =
                    readEnrollments(workbook.getSheet(ENROLLMENTS), students.keySet(), subjects.keySet());
            List<Exam> exams = readExams(workbook.getSheet(EXAMS), subjects);

            if (!errors.isEmpty()) {
                return java.util.Optional.empty();
            }

            List<Student> registered = students.values().stream()
                    .map(student -> new Student(student.getId(), student.getName(), student.getDepartment(),
                            new LinkedHashSet<>(enrollments.getOrDefault(student.getId(), List.of()))))
                    .toList();

            return java.util.Optional.of(new ImportedWorkbook(registered, List.copyOf(faculty), List.copyOf(rooms),
                    List.copyOf(subjects.values()), exams, enrollments, roomAvailable, facultyAvailable,
                    Map.copyOf(facultyEmails)));
        } catch (IOException | RuntimeException e) {
            errors.add("The file could not be read as an .xlsx workbook: " + e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    private Map<String, Student> readStudents(Sheet sheet) {
        Map<String, Student> students = new LinkedHashMap<>();
        Map<String, Integer> columns = headers(sheet);
        if (columns == null) {
            return students;
        }
        for (Row row : dataRows(sheet)) {
            String id = required(sheet, row, columns, "Student ID");
            String name = required(sheet, row, columns, "Name");
            String department = required(sheet, row, columns, "Department");
            if (id == null || name == null || department == null) {
                continue;
            }
            if (students.containsKey(id)) {
                error(sheet, row, "Student ID", "'" + id + "' appears more than once");
                continue;
            }
            students.put(id, new Student(id, name, department, Set.of()));
        }
        return students;
    }

    private void readFaculty(Sheet sheet, List<Faculty> faculty, Map<String, Boolean> availability,
                             Map<String, String> emails) {
        Map<String, Integer> columns = headers(sheet);
        if (columns == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        Map<String, String> emailOwners = new LinkedHashMap<>();
        for (Row row : dataRows(sheet)) {
            String id = required(sheet, row, columns, "Faculty ID");
            String name = required(sheet, row, columns, "Name");
            String department = required(sheet, row, columns, "Department");
            if (id == null || name == null || department == null) {
                continue;
            }
            if (!seen.add(id)) {
                error(sheet, row, "Faculty ID", "'" + id + "' appears more than once");
                continue;
            }
            String email = readEmail(sheet, row, columns, emailOwners, id);
            faculty.add(new Faculty(id, name, department, false, Set.of()));
            availability.put(id, readAvailability(sheet, row, columns, "Available"));
            if (email != null) {
                emails.put(id, email);
            }
        }
    }

    /**
     * The optional Email column, lower-cased.
     *
     * <p>Blank is allowed — a member with no address simply cannot be given an account — but a
     * malformed or repeated address is reported with its row rather than stored, because an
     * account would later be created from it.
     */
    private String readEmail(Sheet sheet, Row row, Map<String, Integer> columns,
                             Map<String, String> emailOwners, String facultyId) {
        String value = optional(row, columns, "Email");
        if (value == null || value.isBlank()) {
            return null;
        }
        String email = value.trim().toLowerCase();
        if (!EMAIL.matcher(email).matches() || email.length() > 254) {
            error(sheet, row, "Email", "'" + value.trim() + "' is not an email address");
            return null;
        }
        String owner = emailOwners.putIfAbsent(email, facultyId);
        if (owner != null) {
            error(sheet, row, "Email", "'" + email + "' is already used by " + owner);
            return null;
        }
        return email;
    }

    private void readRooms(Sheet sheet, List<Room> rooms, Map<String, Boolean> availability) {
        Map<String, Integer> columns = headers(sheet);
        if (columns == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Row row : dataRows(sheet)) {
            String id = required(sheet, row, columns, "Room ID");
            String building = required(sheet, row, columns, "Building");
            Integer capacity = requiredNumber(sheet, row, columns, "Capacity");
            if (id == null || building == null || capacity == null) {
                continue;
            }
            if (capacity <= 0) {
                error(sheet, row, "Capacity", "must be greater than zero, found " + capacity);
                continue;
            }
            if (!seen.add(id)) {
                error(sheet, row, "Room ID", "'" + id + "' appears more than once");
                continue;
            }
            String name = optional(row, columns, "Name");
            rooms.add(Room.ofCapacity(id, name == null || name.isBlank() ? id : name, capacity, building));
            availability.put(id, readAvailability(sheet, row, columns, "Available"));
        }
    }

    private Map<String, Subject> readSubjects(Sheet sheet) {
        Map<String, Subject> subjects = new LinkedHashMap<>();
        Map<String, Integer> columns = headers(sheet);
        if (columns == null) {
            return subjects;
        }
        for (Row row : dataRows(sheet)) {
            String code = required(sheet, row, columns, "Subject Code");
            String name = required(sheet, row, columns, "Subject Name");
            String department = required(sheet, row, columns, "Department");
            if (code == null || name == null || department == null) {
                continue;
            }
            if (subjects.containsKey(code)) {
                error(sheet, row, "Subject Code", "'" + code + "' appears more than once");
                continue;
            }
            String semester = optional(row, columns, "Semester");
            Integer parsed = null;
            if (semester != null && !semester.isBlank()) {
                try {
                    parsed = (int) Double.parseDouble(semester);
                } catch (NumberFormatException e) {
                    error(sheet, row, "Semester", "'" + semester + "' is not a whole number");
                    continue;
                }
            }
            subjects.put(code, new Subject(code, name, department, parsed));
        }
        return subjects;
    }

    private Map<String, List<String>> readEnrollments(Sheet sheet, Set<String> studentIds, Set<String> subjectCodes) {
        Map<String, List<String>> enrollments = new LinkedHashMap<>();
        Map<String, Integer> columns = headers(sheet);
        if (columns == null) {
            return enrollments;
        }
        for (Row row : dataRows(sheet)) {
            String studentId = required(sheet, row, columns, "Student ID");
            String subjectCode = required(sheet, row, columns, "Subject Code");
            if (studentId == null || subjectCode == null) {
                continue;
            }
            if (!studentIds.contains(studentId)) {
                error(sheet, row, "Student ID", "'" + studentId + "' is not listed on the Students worksheet");
                continue;
            }
            if (!subjectCodes.contains(subjectCode)) {
                error(sheet, row, "Subject Code", "'" + subjectCode + "' is not listed on the Subjects worksheet");
                continue;
            }
            List<String> forStudent = enrollments.computeIfAbsent(studentId, key -> new ArrayList<>());
            if (forStudent.contains(subjectCode)) {
                error(sheet, row, "Subject Code", "'" + studentId + "' is already registered for " + subjectCode);
                continue;
            }
            forStudent.add(subjectCode);
        }
        return enrollments;
    }

    private List<Exam> readExams(Sheet sheet, Map<String, Subject> subjects) {
        List<Exam> exams = new ArrayList<>();
        Map<String, Integer> columns = headers(sheet);
        if (columns == null) {
            return exams;
        }
        Set<String> seen = new HashSet<>();
        for (Row row : dataRows(sheet)) {
            String id = required(sheet, row, columns, "Exam ID");
            String subjectCode = required(sheet, row, columns, "Subject Code");
            if (id == null || subjectCode == null) {
                continue;
            }
            if (!seen.add(id)) {
                error(sheet, row, "Exam ID", "'" + id + "' appears more than once");
                continue;
            }
            Subject subject = subjects.get(subjectCode);
            if (subject == null) {
                error(sheet, row, "Subject Code", "'" + subjectCode + "' is not listed on the Subjects worksheet");
                continue;
            }
            LocalDate date = readDate(sheet, row, columns);
            if (date == null) {
                continue;
            }
            LocalTime start = readTime(sheet, row, columns);
            Integer duration = null;
            String minutes = optional(row, columns, "Duration Minutes");
            if (minutes != null && !minutes.isBlank()) {
                try {
                    duration = (int) Double.parseDouble(minutes);
                } catch (NumberFormatException e) {
                    error(sheet, row, "Duration Minutes", "'" + minutes + "' is not a whole number");
                    continue;
                }
                if (duration <= 0) {
                    error(sheet, row, "Duration Minutes", "must be greater than zero, found " + duration);
                    continue;
                }
                if (duration > ExamMode.MAX_OVERRIDE_MINUTES) {
                    error(sheet, row, "Duration Minutes", "must be " + ExamMode.MAX_OVERRIDE_MINUTES
                            + " or fewer, found " + duration);
                    continue;
                }
            }
            exams.add(new Exam(id, subjectCode, subject.name(), date, start, duration));
        }
        return exams;
    }

    private LocalDate readDate(Sheet sheet, Row row, Map<String, Integer> columns) {
        Cell cell = cellOf(row, columns, "Date");
        if (cell == null) {
            error(sheet, row, "Date", "is required");
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String text = text(cell);
        if (text == null || text.isBlank()) {
            error(sheet, row, "Date", "is required");
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            error(sheet, row, "Date", "'" + text + "' is not a date (use yyyy-mm-dd)");
            return null;
        }
    }

    private LocalTime readTime(Sheet sheet, Row row, Map<String, Integer> columns) {
        Cell cell = cellOf(row, columns, "Start Time");
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getLocalDateTimeCellValue().toLocalTime();
        }
        String text = text(cell);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            error(sheet, row, "Start Time", "'" + text + "' is not a time (use hh:mm)");
            return null;
        }
    }

    private boolean readAvailability(Sheet sheet, Row row, Map<String, Integer> columns, String column) {
        String value = optional(row, columns, column);
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalised = value.trim().toLowerCase();
        if (normalised.equals("true") || normalised.equals("yes") || normalised.equals("y") || normalised.equals("1")) {
            return true;
        }
        if (normalised.equals("false") || normalised.equals("no") || normalised.equals("n") || normalised.equals("0")) {
            return false;
        }
        error(sheet, row, column, "'" + value + "' is not yes or no");
        return true;
    }

    /** Column name to index, or null when a required column is missing. */
    private Map<String, Integer> headers(Sheet sheet) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> columns = new LinkedHashMap<>();
        if (header != null) {
            for (Cell cell : header) {
                String name = text(cell);
                if (name != null && !name.isBlank()) {
                    columns.put(name.trim().toLowerCase(), cell.getColumnIndex());
                }
            }
        }
        boolean complete = true;
        for (String required : REQUIRED_COLUMNS.getOrDefault(sheet.getSheetName(), List.of())) {
            if (!columns.containsKey(required.toLowerCase())) {
                errors.add("Worksheet '" + sheet.getSheetName() + "' is missing the column '" + required + "'");
                complete = false;
            }
        }
        return complete ? columns : null;
    }

    private List<Row> dataRows(Sheet sheet) {
        List<Row> rows = new ArrayList<>();
        for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row != null && !isBlank(row)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private boolean isBlank(Row row) {
        for (Cell cell : row) {
            String value = text(cell);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String required(Sheet sheet, Row row, Map<String, Integer> columns, String column) {
        String value = optional(row, columns, column);
        if (value == null || value.isBlank()) {
            error(sheet, row, column, "is required");
            return null;
        }
        return value.trim();
    }

    private Integer requiredNumber(Sheet sheet, Row row, Map<String, Integer> columns, String column) {
        String value = required(sheet, row, columns, column);
        if (value == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            error(sheet, row, column, "'" + value + "' is not a whole number");
            return null;
        }
    }

    private String optional(Row row, Map<String, Integer> columns, String column) {
        Cell cell = cellOf(row, columns, column);
        return cell == null ? null : text(cell);
    }

    private Cell cellOf(Row row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column.toLowerCase());
        return index == null ? null : row.getCell(index);
    }

    /** Reads a cell as text. A formula is refused rather than evaluated. */
    private String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : trimTrailingZero(cell.getNumericCellValue());
            case FORMULA -> {
                errors.add("Worksheet '" + cell.getSheet().getSheetName() + "', row " + (cell.getRowIndex() + 1)
                        + ": formulas are not accepted, paste the values instead");
                yield null;
            }
            default -> null;
        };
    }

    private static String trimTrailingZero(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private void error(Sheet sheet, Row row, String column, String problem) {
        errors.add("Worksheet '" + sheet.getSheetName() + "', row " + (row.getRowNum() + 1)
                + ", column '" + column + "': " + problem);
    }
}
