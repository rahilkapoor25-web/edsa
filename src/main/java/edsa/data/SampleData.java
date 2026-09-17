package edsa.data;

import edsa.core.ExamData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The four demonstration files that ship with the application, in src/main/resources/sample. */
public final class SampleData {

    private SampleData() {
    }

    public static ExamData load() {
        CsvReader reader = new CsvReader();
        try (BufferedReader students = open("students.csv");
             BufferedReader rooms = open("rooms.csv");
             BufferedReader faculty = open("faculty.csv");
             BufferedReader timetable = open("timetable.csv")) {
            return new ExamData(
                    reader.readStudents(students, "students.csv"),
                    reader.readRooms(rooms, "rooms.csv"),
                    reader.readFaculty(faculty, "faculty.csv"),
                    reader.readSlots(timetable, "timetable.csv"));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the bundled sample files", e);
        }
    }

    private static BufferedReader open(String name) {
        InputStream stream = SampleData.class.getResourceAsStream("/sample/" + name);
        if (stream == null) {
            throw new IllegalStateException("sample file is missing from the classpath: " + name);
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
