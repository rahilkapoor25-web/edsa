package edsa.web;

import edsa.core.AbsenceWindow;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;
import edsa.core.LeaveRequest;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Somebody cannot attend: record it, have it reviewed, and — once it is confirmed — cover the
 * rooms it affects and show exactly what moved.
 *
 * <p>Recording and confirming are deliberately two steps. The application does not decide that
 * a professor is ill, so a request sits pending until an administrator confirms or rejects it,
 * and only a confirmation moves a duty. The controller binds the form, asks
 * {@link LeaveWorkflow}, and picks a view; every rule about who may cover what lives in
 * {@code edsa.core}.
 */
@Controller
public class DisruptionController {

    private final Workspace workspace;
    private final LeaveWorkflow leave;

    public DisruptionController(Workspace workspace, LeaveWorkflow leave) {
        this.workspace = workspace;
        this.leave = leave;
    }

    @GetMapping("/disruption")
    public String page(Model model) {
        model.addAttribute("hasPlan", workspace.hasPlan());
        model.addAttribute("mode", workspace.mode());
        model.addAttribute("now", LocalDateTime.now());
        model.addAttribute("requests", leave.requests());
        model.addAttribute("defaultDeadline", leave.defaultDeadline().withSecond(0).withNano(0));

        if (workspace.hasPlan()) {
            ExamPlan plan = workspace.result().plan();
            model.addAttribute("workloads", plan.workloads());
            model.addAttribute("dates", plan.schedule().stream()
                    .map(ExamSlot::getDate).distinct().sorted().toList());
            model.addAttribute("repair", workspace.lastRepair());
            model.addAttribute("firstDay", plan.schedule().stream()
                    .map(ExamSlot::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()));
        }
        return "disruption";
    }

    /**
     * Records an absence. It changes no duty: it is stored as pending and waits for review.
     *
     * <p>A professor may report their own absence, which is why this is open to any signed-in
     * account; deciding it is not.
     */
    @PostMapping("/disruption/leave")
    public String recordLeave(@RequestParam String facultyId,
                              @RequestParam(required = false) String from,
                              @RequestParam(required = false) String to,
                              @RequestParam(required = false) String wholeSession,
                              @RequestParam(required = false) String reason,
                              @RequestParam(required = false) String confirmBy,
                              RedirectAttributes flash) {
        try {
            AbsenceWindow window = windowOf(from, to, wholeSession);
            LeaveRequest recorded = leave.record(facultyId, window, reason, moment(confirmBy, "confirmation deadline"));
            flash.addFlashAttribute("notice", "Recorded " + recorded.facultyName() + "'s absence for "
                    + window.describe() + ". It changes nothing until it is confirmed.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/disruption";
    }

    /** Confirms a request and covers the duties it affects. Administrators only. */
    @PostMapping("/disruption/leave/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmLeave(@PathVariable long id, Principal principal, RedirectAttributes flash) {
        try {
            LeaveRequest decided = leave.confirmAndReassign(id, principal == null ? null : principal.getName());
            flash.addFlashAttribute("notice", "Confirmed " + decided.facultyName() + "'s absence. "
                    + decided.outcome());
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/disruption";
    }

    /** Turns a request down. The duty roster is left exactly as it was. Administrators only. */
    @PostMapping("/disruption/leave/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public String rejectLeave(@PathVariable long id, Principal principal, RedirectAttributes flash) {
        try {
            LeaveRequest decided = leave.reject(id, principal == null ? null : principal.getName());
            flash.addFlashAttribute("notice", "Rejected " + decided.facultyName()
                    + "'s absence. No duty was changed.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/disruption";
    }

    /** The absence the form describes: the whole session, or one explicit interval. */
    private static AbsenceWindow windowOf(String from, String to, String wholeSession) {
        if (wholeSession != null && !wholeSession.isBlank()) {
            return AbsenceWindow.wholeSession();
        }
        LocalDateTime start = moment(from, "start of the absence");
        LocalDateTime end = moment(to, "end of the absence");
        if (start == null || end == null) {
            throw new IllegalArgumentException("Give both the start and the end of the absence, "
                    + "or choose the whole session");
        }
        return AbsenceWindow.between(start, end);
    }

    private static LocalDateTime moment(String value, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'" + value + "' is not a date and time for the " + what);
        }
    }
}
