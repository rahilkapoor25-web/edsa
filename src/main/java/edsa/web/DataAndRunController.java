package edsa.web;

import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.InvalidInputException;
import edsa.core.PlanningResult;
import edsa.data.CsvReader;
import edsa.data.ExamDataRepository;
import edsa.data.ExcelTemplate;
import edsa.data.ImportReport;
import edsa.data.SampleData;
import edsa.data.WorkbookImportService;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Load the data, choose the exam mode, compute the timetable, and see what the rules made of
 * it.
 *
 * <p>The controller reads the request, asks the core or the data layer, and hands the answer
 * to the template. No scheduling or rule logic lives here.
 */
@Controller
public class DataAndRunController {

    private final Workspace workspace;
    private final WorkbookImportService workbooks;
    private final ExamDataRepository repository;
    private final CsvReader reader = new CsvReader();

    public DataAndRunController(Workspace workspace, WorkbookImportService workbooks, ExamDataRepository repository) {
        this.workspace = workspace;
        this.workbooks = workbooks;
        this.repository = repository;
    }

    @GetMapping("/")
    public String page(Model model) {
        model.addAttribute("modes", ExamMode.values());
        model.addAttribute("mode", workspace.mode());
        model.addAttribute("hasData", workspace.hasData());
        model.addAttribute("storedData", repository.hasData());

        if (workspace.hasData()) {
            ExamData data = workspace.data();
            model.addAttribute("studentCount", data.students().size());
            model.addAttribute("roomCount", data.rooms().size());
            model.addAttribute("facultyCount", data.faculty().size());
            model.addAttribute("examCount", data.exams().size());
            model.addAttribute("exams", data.exams());
            model.addAttribute("seatCount", data.rooms().stream().mapToInt(room -> room.capacity()).sum());
            model.addAttribute("ignoredOverrides", data.ignoredDurationOverrides(workspace.mode()));
        }

        PlanningResult result = workspace.result();
        model.addAttribute("hasPlan", result != null);
        if (result != null) {
            model.addAttribute("checks", result.checks());
            model.addAttribute("usable", result.isUsable());
            model.addAttribute("problems", result.problems());
            model.addAttribute("schedule", result.plan().schedule());
            model.addAttribute("scheduleRows", result.plan().scheduledPapers());
            model.addAttribute("seated", result.plan().seating().seatedCount());
            model.addAttribute("dutyCount", result.plan().duties().all().size());
        }
        return "data-and-run";
    }

    @PostMapping("/mode")
    public String chooseMode(@RequestParam String mode, RedirectAttributes flash) {
        try {
            workspace.setMode(ExamMode.of(mode));
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/data/workbook")
    public String uploadWorkbook(@RequestParam MultipartFile workbook, RedirectAttributes flash) {
        if (workbook == null || workbook.isEmpty()) {
            flash.addFlashAttribute("error", "Choose an .xlsx workbook to upload");
            return "redirect:/";
        }
        ImportReport report;
        try {
            report = workbooks.importWorkbook(workbook.getOriginalFilename(), workbook.getSize(),
                    workbook.getInputStream());
        } catch (IOException e) {
            flash.addFlashAttribute("error", "The upload could not be read: " + e.getMessage());
            return "redirect:/";
        }

        flash.addFlashAttribute("report", report);
        if (report.successful()) {
            workspace.setData(repository.load());
        }
        return "redirect:/";
    }

    @PostMapping("/data/stored")
    public String useStoredData(RedirectAttributes flash) {
        if (!repository.hasData()) {
            flash.addFlashAttribute("error", "No workbook has been imported yet");
            return "redirect:/";
        }
        workspace.setData(repository.load());
        return "redirect:/";
    }

    @PostMapping("/data/sample")
    public String loadSample() {
        workspace.setData(SampleData.load());
        return "redirect:/";
    }

    @PostMapping("/data/upload")
    public String uploadCsv(@RequestParam MultipartFile students,
                            @RequestParam MultipartFile rooms,
                            @RequestParam MultipartFile faculty,
                            @RequestParam MultipartFile timetable,
                            RedirectAttributes flash) {
        try {
            workspace.setData(new ExamData(
                    reader.readStudents(contentOf(students), nameOf(students, "students.csv")),
                    reader.readRooms(contentOf(rooms), nameOf(rooms, "rooms.csv")),
                    reader.readFaculty(contentOf(faculty), nameOf(faculty, "faculty.csv")),
                    reader.readExams(contentOf(timetable), nameOf(timetable, "timetable.csv"))));
        } catch (InvalidInputException | UncheckedIOException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/compute")
    public String compute(RedirectAttributes flash) {
        if (!workspace.hasData()) {
            flash.addFlashAttribute("error", "Load the data first");
            return "redirect:/";
        }
        workspace.compute();
        return "redirect:/";
    }

    @GetMapping("/import-template.xlsx")
    public ResponseEntity<byte[]> downloadTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"edsa-import-template.xlsx\"")
                .contentType(MediaType.valueOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(ExcelTemplate.write());
    }

    private static Reader contentOf(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new InvalidInputException(nameOf(upload, "file"), 1, "no file was chosen");
        }
        try {
            return new InputStreamReader(upload.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the uploaded file", e);
        }
    }

    private static String nameOf(MultipartFile upload, String fallback) {
        if (upload == null) {
            return fallback;
        }
        String name = upload.getOriginalFilename();
        return name == null || name.isBlank() ? fallback : name;
    }
}
