package edsa.web;

import edsa.core.ExamPlan;
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

    @GetMapping("/rooms")
    public String page(@RequestParam(required = false) String room, Model model) {
        model.addAttribute("hasPlan", workspace.hasPlan());
        if (workspace.hasPlan()) {
            ExamPlan plan = workspace.plan();
            model.addAttribute("rooms", plan.roomsUsed());
            model.addAttribute("room", room);
            model.addAttribute("sittings", room == null || room.isBlank()
                    ? plan.sittings()
                    : plan.sittingsIn(room));
        }
        return "room-schedule";
    }

    @GetMapping("/seating-plan.csv")
    public ResponseEntity<byte[]> download() {
        if (!workspace.hasPlan()) {
            return ResponseEntity.notFound().build();
        }
        byte[] csv = SeatingPlanCsv.write(workspace.plan()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"seating-plan.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
