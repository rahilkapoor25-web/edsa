package edsa.web;

import edsa.core.Designation;
import edsa.core.ExamPlan;
import edsa.core.Faculty;
import edsa.core.SeniorityLadder;
import edsa.core.Workload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Duty allocation: what the allocator recommends, what a coordinator has approved, and how
 * evenly the work has landed.
 *
 * <p>The page decides nothing. Which colleague may take which room is
 * {@link edsa.core.DutyEligibility}'s answer, how loaded somebody is relative to their rank is
 * the same class's, and the fairness figures below are read off the plan rather than computed
 * into one. What is here is binding a form, asking {@link DutyWorkflow}, and picking a view.
 */
@Controller
public class DutyController {

    private final Workspace workspace;
    private final DutyWorkflow duties;
    private final SignedInActor actor;

    public DutyController(Workspace workspace, DutyWorkflow duties, SignedInActor actor) {
        this.workspace = workspace;
        this.duties = duties;
        this.actor = actor;
    }

    /** One invigilator's share of the work, next to what their rank was expected to carry. */
    public record FairnessRow(Faculty member, String designationLabel, int duties,
                              double expected, double share, boolean senior) {

        /** How far above or below their expected share this person landed. */
        public double gap() {
            return duties - expected;
        }

        public boolean isOverloaded() {
            return gap() >= 1;
        }

        public boolean isUnderloaded() {
            return gap() <= -1;
        }

        /** A width between 4 and 100 for the bar on the page, so even one duty is visible. */
        public int barPercent(int mostDuties) {
            if (mostDuties <= 0) {
                return 0;
            }
            return Math.max(duties == 0 ? 0 : 4, (int) Math.round(100.0 * duties / mostDuties));
        }
    }

    @GetMapping("/duties")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'PROFESSOR')")
    public String page(Authentication authentication, Model model,
                       @RequestParam(required = false) String filter) {
        model.addAttribute("hasPlan", workspace.hasPlan());
        model.addAttribute("hasData", workspace.hasData());
        model.addAttribute("mode", workspace.mode());
        model.addAttribute("ladder", workspace.ladder().designations());
        model.addAttribute("policy", workspace.policy());
        model.addAttribute("canApprove", actor.isCoordinator(authentication));
        model.addAttribute("filter", filter == null ? "all" : filter);

        List<DutyWorkflow.DutyRow> rows = duties.rows();
        model.addAttribute("rows", filtered(rows, filter));
        model.addAttribute("totalRooms", rows.size());
        model.addAttribute("unstaffed", rows.stream().filter(DutyWorkflow.DutyRow::isUnstaffed).count());
        model.addAttribute("awaitingReview", rows.stream().filter(DutyWorkflow.DutyRow::awaitingReview).count());
        model.addAttribute("approved", rows.stream()
                .filter(row -> !row.isUnstaffed() && row.origin().decidedByPerson()).count());

        if (workspace.hasPlan()) {
            model.addAttribute("fairness", fairness());
            model.addAttribute("mostDuties", fairness().stream()
                    .mapToInt(FairnessRow::duties).max().orElse(0));
            model.addAttribute("spread", spread());
            model.addAttribute("faculty", workspace.data() == null ? List.of() : workspace.data().faculty());
        }
        if (workspace.hasData()) {
            model.addAttribute("unrecognisedRanks",
                    workspace.ladder().unrecognisedIn(workspace.data().faculty()));
        }
        return "duties";
    }

    private List<DutyWorkflow.DutyRow> filtered(List<DutyWorkflow.DutyRow> rows, String filter) {
        if (filter == null || filter.isBlank() || "all".equals(filter)) {
            return rows;
        }
        return switch (filter) {
            case "unstaffed" -> rows.stream().filter(DutyWorkflow.DutyRow::isUnstaffed).toList();
            case "review" -> rows.stream().filter(DutyWorkflow.DutyRow::awaitingReview).toList();
            case "approved" -> rows.stream()
                    .filter(row -> !row.isUnstaffed() && row.origin().decidedByPerson()).toList();
            default -> rows;
        };
    }

    /**
     * Everybody's duties against their expected share.
     *
     * <p>Built over the whole faculty and not only those on duty: somebody with no duties at all
     * is the most interesting row on a fairness table, and listing only the people who got work
     * would hide them.
     */
    private List<FairnessRow> fairness() {
        ExamPlan plan = workspace.result().plan();
        SeniorityLadder ladder = workspace.ladder();
        List<Faculty> all = plan.data().faculty();

        int totalDuties = plan.duties().all().size();
        double totalShares = all.stream()
                .mapToDouble(member -> workspace.policy().weightBySeniority() ? ladder.dutyShareOf(member) : 1.0)
                .sum();

        List<FairnessRow> rows = new ArrayList<>();
        for (Faculty member : all) {
            int held = plan.duties().forFaculty(member.getId()).size();
            rows.add(new FairnessRow(member,
                    ladder.find(member.getDesignation()).map(Designation::label)
                            .orElse(member.getDesignation() == null ? "Not given"
                                    : member.getDesignation() + " (unrecognised)"),
                    held,
                    workspace.eligibility().fairShareOf(member, totalDuties, totalShares),
                    workspace.policy().weightBySeniority() ? ladder.dutyShareOf(member) : 1.0,
                    ladder.isSenior(member)));
        }
        rows.sort(Comparator.comparingInt(FairnessRow::duties).reversed()
                .thenComparing(row -> row.member().getId()));
        return rows;
    }

    /**
     * The gap between the busiest and the quietest invigilator, which is the one number that
     * says whether the spreading worked.
     */
    private int spread() {
        List<Workload> workloads = workspace.result().plan().workloads();
        if (workloads.isEmpty()) {
            return 0;
        }
        int most = workloads.stream().mapToInt(Workload::duties).max().orElse(0);
        int fewest = workspace.result().plan().data().faculty().size() > workloads.size()
                ? 0
                : workloads.stream().mapToInt(Workload::duties).min().orElse(0);
        return most - fewest;
    }

    @PostMapping("/duties/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String approve(@RequestParam String slotId, @RequestParam String roomId,
                          Authentication authentication, RedirectAttributes flash) {
        try {
            duties.approve(slotId, roomId, actor.emailOf(authentication));
            flash.addFlashAttribute("notice", "Approved the invigilator for room " + roomId);
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/duties";
    }

    @PostMapping("/duties/approve-all")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String approveAll(Authentication authentication, RedirectAttributes flash) {
        try {
            int approved = duties.approveAllRecommended(actor.emailOf(authentication));
            flash.addFlashAttribute("notice", approved == 0
                    ? "There was nothing left awaiting review"
                    : "Approved " + approved + (approved == 1 ? " duty" : " duties"));
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/duties";
    }

    @PostMapping("/duties/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String assign(@RequestParam String slotId, @RequestParam String roomId,
                         @RequestParam String facultyId, @RequestParam(required = false) String note,
                         Authentication authentication, RedirectAttributes flash) {
        try {
            duties.assignByHand(slotId, roomId, facultyId, actor.emailOf(authentication), note);
            flash.addFlashAttribute("notice", "Room " + roomId + " is now assigned to " + facultyId);
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/duties";
    }

    @PostMapping("/duties/clear")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String clear(@RequestParam String slotId, @RequestParam String roomId,
                        RedirectAttributes flash) {
        try {
            duties.clearDecision(slotId, roomId);
            flash.addFlashAttribute("notice", "Room " + roomId + " is back on the allocator's recommendation");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/duties";
    }
}
