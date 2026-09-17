package edsa.web;

import edsa.core.CapacityException;
import edsa.core.ExamData;
import edsa.core.ExamPlan;
import edsa.core.GreedyAllocator;
import edsa.core.InvalidInputException;
import edsa.core.NoInvigilatorAvailableException;
import edsa.core.PlanChecker;
import edsa.data.CsvReader;
import edsa.data.SampleData;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Load the four files, then compute a plan from them and see what the rules made of it. */
@Controller
public class DataAndRunController {

    private final Workspace workspace;
    private final CsvReader reader = new CsvReader();
    private final PlanChecker checker = PlanChecker.standard();
    private final GreedyAllocator allocator = new GreedyAllocator(PlanChecker.standard());

    public DataAndRunController(Workspace workspace) {
        this.workspace = workspace;
    }

    @GetMapping("/")
    public String page(Model model) {
        model.addAttribute("hasData", workspace.hasData());
        model.addAttribute("hasPlan", workspace.hasPlan());

        if (workspace.hasData()) {
            ExamData data = workspace.data();
            model.addAttribute("studentCount", data.students().size());
            model.addAttribute("roomCount", data.rooms().size());
            model.addAttribute("facultyCount", data.faculty().size());
            model.addAttribute("slotCount", data.slots().size());
            model.addAttribute("slots", data.slots());
        }
        if (workspace.hasPlan()) {
            ExamPlan plan = workspace.plan();
            model.addAttribute("checks", checker.inspect(plan));
            model.addAttribute("score", checker.score(plan));
            model.addAttribute("seated", plan.seating().seatedCount());
            model.addAttribute("dutyCount", plan.duties().all().size());
        }
        return "data-and-run";
    }

    @PostMapping("/data/sample")
    public String loadSample() {
        workspace.setData(SampleData.load());
        return "redirect:/";
    }

    @PostMapping("/data/upload")
    public String upload(@RequestParam MultipartFile students,
                         @RequestParam MultipartFile rooms,
                         @RequestParam MultipartFile faculty,
                         @RequestParam MultipartFile timetable,
                         RedirectAttributes flash) {
        try {
            workspace.setData(new ExamData(
                    reader.readStudents(contentOf(students), nameOf(students, "students.csv")),
                    reader.readRooms(contentOf(rooms), nameOf(rooms, "rooms.csv")),
                    reader.readFaculty(contentOf(faculty), nameOf(faculty, "faculty.csv")),
                    reader.readSlots(contentOf(timetable), nameOf(timetable, "timetable.csv"))));
        } catch (InvalidInputException | UncheckedIOException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/compute")
    public String compute(RedirectAttributes flash) {
        if (!workspace.hasData()) {
            flash.addFlashAttribute("error", "Load the four files first");
            return "redirect:/";
        }
        try {
            workspace.setPlan(allocator.allocate(workspace.data()));
        } catch (CapacityException | NoInvigilatorAvailableException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    private static Reader contentOf(MultipartFile upload) {
        if (upload.isEmpty()) {
            throw new InvalidInputException(nameOf(upload, "file"), 1, "no file was chosen");
        }
        try {
            return new InputStreamReader(upload.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the uploaded file", e);
        }
    }

    private static String nameOf(MultipartFile upload, String fallback) {
        String name = upload.getOriginalFilename();
        return name == null || name.isBlank() ? fallback : name;
    }
}
