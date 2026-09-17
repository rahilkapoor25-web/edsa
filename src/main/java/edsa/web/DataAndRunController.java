package edsa.web;

import edsa.core.ExamData;
import edsa.core.ExamSlot;
import edsa.core.Faculty;
import edsa.core.Room;
import edsa.core.Student;
import edsa.data.SampleData;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The landing page: what has been loaded, before anything is solved. */
@Controller
public class DataAndRunController {

    private final ExamData data = SampleData.load();

    @GetMapping("/")
    public String dataAndRun(Model model) {
        model.addAttribute("studentCount", data.students().size());
        model.addAttribute("roomCount", data.rooms().size());
        model.addAttribute("facultyCount", data.faculty().size());
        model.addAttribute("slotCount", data.slots().size());
        model.addAttribute("departmentCount",
                data.students().stream().map(Student::getDepartment).distinct().count());
        model.addAttribute("totalSeats", data.rooms().stream().mapToInt(Room::capacity).sum());
        model.addAttribute("seniorCount", data.faculty().stream().filter(Faculty::isSenior).count());
        model.addAttribute("examDays", data.slots().stream().map(ExamSlot::getDate).distinct().count());
        model.addAttribute("slots", data.slots());
        model.addAttribute("rooms", data.rooms());
        model.addAttribute("candidates", candidateCounts());
        return "data-and-run";
    }

    private Map<String, Integer> candidateCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        data.slots().forEach(slot -> counts.put(slot.getId(), data.studentsFor(slot.getPaperCode()).size()));
        return counts;
    }
}
