package edsa.data;

import edsa.core.Exam;
import edsa.core.ExamData;
import edsa.core.Faculty;
import edsa.core.Room;
import edsa.core.SchedulingConfig;
import edsa.core.Student;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates an uploaded workbook and, only when every sheet and row is sound, replaces the
 * stored reference data with it.
 *
 * <p>Parsing happens before anything is written, and the write runs in one transaction, so a
 * workbook that fails validation cannot leave the database partly updated. {@link #check} runs
 * the very same validation and stores nothing, which is what lets the page enable its Generate
 * schedule button only for a workbook the import would accept.
 */
@Service
public class WorkbookImportService {

    /** Twelve megabytes is far more than a session's worth of rows and far less than a bomb. */
    public static final long MAX_UPLOAD_BYTES = 12L * 1024 * 1024;

    private final ExamDataRepository repository;

    public WorkbookImportService(ExamDataRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportReport importWorkbook(String fileName, long sizeInBytes, InputStream content) {
        Validated validated = validate(fileName, sizeInBytes, content);
        if (validated.workbook() != null) {
            repository.replaceAll(validated.workbook());
        }
        return validated.report();
    }

    /** Everything {@link #importWorkbook} would refuse, reported without storing anything. */
    public ImportReport check(String fileName, long sizeInBytes, InputStream content) {
        return validate(fileName, sizeInBytes, content).report();
    }

    /** The report, and the parsed workbook when — and only when — it passed. */
    private record Validated(ImportReport report, ImportedWorkbook workbook) {

        static Validated failed(List<String> errors) {
            return new Validated(ImportReport.failed(errors), null);
        }
    }

    private Validated validate(String fileName, long sizeInBytes, InputStream content) {
        if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
            return Validated.failed(List.of("Only .xlsx workbooks can be uploaded"));
        }
        if (sizeInBytes <= 0) {
            return Validated.failed(List.of("The uploaded file is empty"));
        }
        if (sizeInBytes > MAX_UPLOAD_BYTES) {
            return Validated.failed(List.of("The workbook is larger than "
                    + (MAX_UPLOAD_BYTES / (1024 * 1024)) + " MB"));
        }

        ExcelWorkbookReader reader = new ExcelWorkbookReader();
        Optional<ImportedWorkbook> parsed;
        try (InputStream source = content) {
            parsed = reader.read(source);
        } catch (IOException e) {
            return Validated.failed(List.of("The upload could not be read: " + e.getMessage()));
        }
        if (parsed.isEmpty()) {
            return Validated.failed(reader.errors());
        }

        ImportedWorkbook workbook = parsed.get();
        List<String> missing = whatSchedulingLacks(workbook);
        if (!missing.isEmpty()) {
            return Validated.failed(missing);
        }
        return new Validated(ImportReport.succeeded(workbook), workbook);
    }

    /**
     * What a workbook can be sound row by row and still lack: something to schedule, somewhere
     * to seat it, somebody to invigilate it, candidates for every paper, a paper for every
     * candidate, and enough seats for the largest paper. These hold on the reference sample
     * workbook, and each one left unchecked would make a plan that silently leaves someone out.
     */
    private static List<String> whatSchedulingLacks(ImportedWorkbook workbook) {
        if (workbook.exams().isEmpty()) {
            return List.of("The Exams worksheet has no rows, so there is nothing to schedule");
        }
        List<String> problems = new ArrayList<>();
        ExamData planning = workbook.toExamData();
        if (planning.rooms().isEmpty()) {
            problems.add("No room on the Rooms worksheet is available, so nobody can be seated");
        }
        if (planning.faculty().isEmpty()) {
            problems.add("Nobody on the Faculty worksheet is available to invigilate");
        } else if (planning.faculty().stream().noneMatch(Faculty::isSenior)) {
            problems.add("No available Professor is on the Faculty worksheet, and every room needs one");
        }

        Map<String, Integer> cohorts = new HashMap<>();
        workbook.enrollments().values().forEach(subjects ->
                subjects.forEach(subject -> cohorts.merge(subject, 1, Integer::sum)));
        int seats = planning.rooms().stream().mapToInt(Room::capacity).sum();
        for (Exam exam : workbook.exams()) {
            int cohort = cohorts.getOrDefault(exam.getSubjectCode(), 0);
            if (cohort == 0) {
                problems.add("Exams: " + exam.getId() + " (" + exam.getSubjectCode()
                        + ") has no students on the Enrollments worksheet");
            } else if (!planning.rooms().isEmpty() && cohort > seats) {
                problems.add("Exams: " + exam.getId() + " (" + exam.getSubjectCode() + ") has " + cohort
                        + " students but the available rooms seat only " + seats + " in total");
            }
            // Its own department's staff may not invigilate it, so enough must come from elsewhere.
            List<Faculty> outside = planning.faculty().stream()
                    .filter(member -> !member.belongsTo(exam.getDepartment())).toList();
            if (outside.stream().noneMatch(Faculty::isSenior)) {
                problems.add("Exams: " + exam.getId() + " (" + exam.getSubjectCode() + ", " + exam.getDepartment()
                        + ") has no available Professor from outside " + exam.getDepartment() + " to invigilate it");
            } else if (outside.size() < SchedulingConfig.INVIGILATORS_PER_ROOM) {
                problems.add("Exams: " + exam.getId() + " (" + exam.getSubjectCode() + ", " + exam.getDepartment()
                        + ") needs " + SchedulingConfig.INVIGILATORS_PER_ROOM + " invigilators per room from outside "
                        + exam.getDepartment() + " but only " + outside.size() + " are available");
            }
        }
        for (Student student : workbook.students()) {
            if (workbook.enrollments().getOrDefault(student.getId(), List.of()).isEmpty()) {
                problems.add("Students: " + student.getId() + " is not enrolled in any subject on the "
                        + "Enrollments worksheet");
            }
        }
        return problems;
    }
}
