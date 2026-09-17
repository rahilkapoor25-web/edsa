package edsa.data;

import edsa.core.ExamSlot;
import edsa.core.Faculty;
import edsa.core.InvalidInputException;
import edsa.core.Room;
import edsa.core.Student;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Loads the four input files.
 *
 * <p>Plain comma-separated values: no quoted fields, no embedded commas. Every row is
 * checked, and a row that cannot be understood is reported with its file name and line
 * number rather than skipped.
 */
public final class CsvReader {

    private static final String CODE_SEPARATOR = ";";

    public List<Student> readStudents(Path file) {
        return read(file, 4, Student::getId, fields -> new Student(
                requireText(fields[0], "student_id"),
                requireText(fields[1], "name"),
                requireText(fields[2], "department"),
                requireCodes(fields[3], "papers")));
    }

    public List<Room> readRooms(Path file) {
        return read(file, 5, Room::getId, fields -> new Room(
                requireText(fields[0], "room_id"),
                requireText(fields[1], "name"),
                requirePositiveInt(fields[2], "rows"),
                requirePositiveInt(fields[3], "columns"),
                requireText(fields[4], "building")));
    }

    public List<Faculty> readFaculty(Path file) {
        return read(file, 5, Faculty::getId, fields -> new Faculty(
                requireText(fields[0], "faculty_id"),
                requireText(fields[1], "name"),
                requireText(fields[2], "department"),
                requireBoolean(fields[3], "senior"),
                optionalCodes(fields[4])));
    }

    public List<ExamSlot> readSlots(Path file) {
        return read(file, 6, ExamSlot::getId, fields -> {
            LocalTime start = requireTime(fields[4], "start_time");
            LocalTime end = requireTime(fields[5], "end_time");
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("end_time " + fields[5] + " is not after start_time " + fields[4]);
            }
            return new ExamSlot(
                    requireText(fields[0], "slot_id"),
                    requireText(fields[1], "paper_code"),
                    requireText(fields[2], "paper_name"),
                    requireDate(fields[3], "exam_date"),
                    start,
                    end);
        });
    }

    @FunctionalInterface
    private interface RowParser<T> {
        T parse(String[] fields);
    }

    private <T> List<T> read(Path file, int expectedColumns, Function<T, String> idOf, RowParser<T> parser) {
        String fileName = file.getFileName().toString();
        List<T> rows = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            if (reader.readLine() == null) {
                throw new InvalidInputException(fileName, 1, "file is empty");
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = Arrays.stream(line.split(",", -1)).map(String::trim).toArray(String[]::new);
                if (fields.length != expectedColumns) {
                    throw new InvalidInputException(fileName, lineNumber,
                            "expected " + expectedColumns + " columns, found " + fields.length);
                }
                T row;
                try {
                    row = parser.parse(fields);
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    throw new InvalidInputException(fileName, lineNumber, e.getMessage());
                }
                if (!seenIds.add(idOf.apply(row))) {
                    throw new InvalidInputException(fileName, lineNumber, "duplicate id " + idOf.apply(row));
                }
                rows.add(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        return rows;
    }

    private static String requireText(String value, String field) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is blank");
        }
        return value;
    }

    private static Set<String> requireCodes(String value, String field) {
        Set<String> codes = optionalCodes(value);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException(field + " is empty");
        }
        return codes;
    }

    private static Set<String> optionalCodes(String value) {
        Set<String> codes = new LinkedHashSet<>();
        for (String code : value.split(CODE_SEPARATOR)) {
            String trimmed = code.trim();
            if (!trimmed.isEmpty()) {
                codes.add(trimmed);
            }
        }
        return codes;
    }

    private static int requirePositiveInt(String value, String field) {
        int number;
        try {
            number = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " is not a whole number: '" + value + "'");
        }
        if (number <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero, found " + number);
        }
        return number;
    }

    private static boolean requireBoolean(String value, String field) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException(field + " must be true or false, found '" + value + "'");
    }

    private static LocalDate requireDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " is not a date (yyyy-mm-dd): '" + value + "'");
        }
    }

    private static LocalTime requireTime(String value, String field) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " is not a time (hh:mm): '" + value + "'");
        }
    }
}
