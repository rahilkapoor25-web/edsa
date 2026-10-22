package edsa.web;

import edsa.core.ExamPlan;
import edsa.core.RoomSitting;
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
     * One page, three things it can be showing.
     *
     * <ul>
     *   <li>A room detail — one paper in one room, with its seating grid — when a duty has
     *       been opened. This is the only view that shows seats.
     *   <li>An invigilator's duties, when a name has been searched. Just theirs: the whole
     *       institution's sittings underneath would bury the answer they came for.
     *   <li>Otherwise the sittings for the session, optionally narrowed to one room.
     * </ul>
     */
    @GetMapping("/rooms")
    public String page(@RequestParam(required = false) String room,
                       @RequestParam(required = false) String professor,
                       @RequestParam(required = false) String slot,
                       Model model) {
        boolean searched = professor != null && !professor.isBlank();
        model.addAttribute("hasPlan", workspace.hasPlan());
        model.addAttribute("mode", workspace.mode());
        model.addAttribute("room", room);
        model.addAttribute("professor", professor);
        model.addAttribute("searched", searched);

        if (!workspace.hasPlan()) {
            return "room-schedule";
        }

        ExamPlan plan = workspace.result().plan();
        model.addAttribute("rooms", plan.roomsUsed());

        RoomSitting focus = plan.sitting(slot, room).orElse(null);
        model.addAttribute("focus", focus);
        if (focus != null) {
            model.addAttribute("grid", focus.grid());
            return "room-schedule";
        }
        // A link was followed to a pairing the plan does not hold — most likely the session
        // was recomputed underneath it. Say so, rather than quietly showing something else.
        model.addAttribute("missingSitting", slot != null && !slot.isBlank());

        if (searched) {
            model.addAttribute("matches", plan.invigilatorsNamed(professor));
            return "room-schedule";
        }

        var sittings = room == null || room.isBlank() ? plan.sittings() : plan.sittingsIn(room);
        model.addAttribute("sittings", sittings);
        model.addAttribute("seatedTotal", sittings.stream().mapToInt(RoomSitting::seated).sum());
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
