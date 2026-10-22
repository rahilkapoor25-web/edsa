package edsa.web;

import edsa.core.ExamPlan;
import edsa.data.PlanWorkbook;
import edsa.data.SeatingPlanCsv;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The seating plan, one row per seat, narrowed to a room on request. */
@Controller
public class RoomScheduleController {

    private final Workspace workspace;

    public RoomScheduleController(Workspace workspace) {
        this.workspace = workspace;
    }

    /**
     * The seating plan, optionally narrowed to one room, and optionally searched for an
     * invigilator by name.
     *
     * <p>The two filters are independent and both live in one form, so a submit keeps
     * whichever of them is set. A plain GET form is deliberate: this application has no
     * JavaScript at all, and a search box that works without any is simpler, accessible, and
     * bookmarkable.
     */
    @GetMapping("/rooms")
    public String page(@RequestParam(required = false) String room,
                       @RequestParam(required = false) String professor,
                       Model model) {
        model.addAttribute("hasPlan", workspace.hasPlan());
        model.addAttribute("mode", workspace.mode());
        model.addAttribute("room", room);
        model.addAttribute("professor", professor);
        model.addAttribute("searched", professor != null && !professor.isBlank());

        if (workspace.hasPlan()) {
            ExamPlan plan = workspace.result().plan();
            model.addAttribute("rooms", plan.roomsUsed());
            var sittings = room == null || room.isBlank() ? plan.sittings() : plan.sittingsIn(room);
            model.addAttribute("sittings", sittings);
            model.addAttribute("seatedTotal", sittings.stream().mapToInt(s -> s.seated()).sum());
            model.addAttribute("matches", plan.invigilatorsNamed(professor));
        }
        return "room-schedule";
    }

    @GetMapping("/exam-plan.xlsx")
    public ResponseEntity<byte[]> downloadWorkbook() {
        if (!workspace.hasPlan()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"edsa-exam-plan.xlsx\"")
                .contentType(MediaType.valueOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(PlanWorkbook.write(workspace.result().plan()));
    }

    @GetMapping("/seating-plan.csv")
    public ResponseEntity<byte[]> download() {
        if (!workspace.hasPlan()) {
            return ResponseEntity.notFound().build();
        }
        byte[] csv = SeatingPlanCsv.write(workspace.result().plan()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"seating-plan.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
