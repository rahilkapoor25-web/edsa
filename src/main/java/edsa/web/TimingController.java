package edsa.web;

import edsa.core.ExamMode;
import edsa.core.TimingOverride;
import edsa.core.TimingReview;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
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
 * Examination timing: move one paper, move a whole day, and see who moved what.
 *
 * <p>The review step is the point of the page. A change is checked against the plan it would
 * produce and the findings are shown first, so a coordinator sees the student with two papers
 * at once before they commit to causing it. Nothing is decided here: the findings come from
 * {@link edsa.core.TimingValidator}, which gets them by planning the change with the real
 * engine rather than by any opinion held in the web layer.
 */
@Controller
public class TimingController {

    private static final int HISTORY_SHOWN = 50;

    private final Workspace workspace;
    private final TimingWorkflow timings;
    private final SignedInActor actor;

    public TimingController(Workspace workspace, TimingWorkflow timings, SignedInActor actor) {
        this.workspace = workspace;
        this.timings = timings;
        this.actor = actor;
    }

    @GetMapping("/timings")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String page(Model model) {
        populate(model);
        return "timings";
    }

    private void populate(Model model) {
        model.addAttribute("hasData", workspace.hasData());
        model.addAttribute("hasPlan", workspace.hasPlan());
        model.addAttribute("mode", workspace.mode());
        model.addAttribute("papers", timings.papers());
        model.addAttribute("history", timings.history(HISTORY_SHOWN));
        model.addAttribute("minMinutes", ExamMode.MIN_APPROVED_MINUTES);
        model.addAttribute("maxMinutes", ExamMode.MAX_OVERRIDE_MINUTES);
        model.addAttribute("dayStart", workspace.config().dayStart());
        model.addAttribute("dayEnd", workspace.config().dayEnd());
        model.addAttribute("dates", timings.papers().stream()
                .map(TimingWorkflow.PaperTiming::date).distinct().sorted().toList());
    }

    /**
     * Reviews a change without saving it, and renders the findings beside the form.
     *
     * <p>Rendered rather than redirected to, because the result carries the warnings and the
     * values that produced them: a redirect would have to put them in the session or the query
     * string, and the coordinator would lose what they typed.
     */
    @PostMapping("/timings/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String review(@RequestParam String examId,
                         @RequestParam(required = false) String date,
                         @RequestParam(required = false) String start,
                         @RequestParam(required = false) Integer minutes,
                         Model model) {
        populate(model);
        try {
            TimingOverride proposal = proposalOf(examId, date, start, minutes);
            model.addAttribute("review", timings.review(proposal));
            model.addAttribute("proposal", proposal);
            model.addAttribute("reviewedExamId", examId);
        } catch (IllegalArgumentException badInput) {
            model.addAttribute("error", badInput.getMessage());
        }
        return "timings";
    }

    @PostMapping("/timings/save")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String save(@RequestParam String examId,
                       @RequestParam(required = false) String date,
                       @RequestParam(required = false) String start,
                       @RequestParam(required = false) Integer minutes,
                       @RequestParam(defaultValue = "false") boolean acceptWarnings,
                       Authentication authentication, Model model, RedirectAttributes flash) {
        try {
            TimingOverride proposal = proposalOf(examId, date, start, minutes);
            TimingReview review = timings.save(proposal, actor.emailOf(authentication), acceptWarnings);

            if (review.isBlocked()) {
                populate(model);
                model.addAttribute("review", review);
                model.addAttribute("proposal", proposal);
                model.addAttribute("reviewedExamId", examId);
                model.addAttribute("error", "That change cannot be made");
                return "timings";
            }
            if (review.hasWarnings() && !acceptWarnings) {
                // Shown rather than saved: the coordinator has to confirm the warnings before
                // the change is written, and the confirmation is a second, explicit submit.
                populate(model);
                model.addAttribute("review", review);
                model.addAttribute("proposal", proposal);
                model.addAttribute("reviewedExamId", examId);
                model.addAttribute("needsConfirmation", true);
                return "timings";
            }
            flash.addFlashAttribute("notice", review.hasWarnings()
                    ? "Saved, with " + review.warnings().size() + " warning(s) recorded against the change"
                    : "Saved the new timing for " + examId);
        } catch (IllegalArgumentException badInput) {
            flash.addFlashAttribute("error", badInput.getMessage());
        }
        return "redirect:/timings";
    }

    @PostMapping("/timings/session")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String shiftSession(@RequestParam String date,
                               @RequestParam(required = false) String start,
                               @RequestParam(required = false) Integer minutes,
                               @RequestParam(defaultValue = "false") boolean acceptWarnings,
                               Authentication authentication, Model model, RedirectAttributes flash) {
        try {
            LocalDate day = LocalDate.parse(date.trim());
            LocalTime newStart = start == null || start.isBlank() ? null : LocalTime.parse(start.trim());
            if (newStart == null && minutes == null) {
                throw new IllegalArgumentException("Give a new start time, a new length, or both");
            }
            TimingWorkflow.SessionShift shift =
                    timings.shiftSession(day, newStart, minutes, actor.emailOf(authentication), acceptWarnings);

            if (!shift.succeeded()) {
                populate(model);
                model.addAttribute("sessionShift", shift);
                model.addAttribute("sessionDate", date);
                model.addAttribute("sessionStart", start);
                model.addAttribute("sessionMinutes", minutes);
                model.addAttribute("error", shift.blockers().isEmpty()
                        ? "That change to the whole day needs confirming"
                        : "That change to the whole day cannot be made");
                return "timings";
            }
            flash.addFlashAttribute("notice", "Moved " + shift.moved()
                    + (shift.moved() == 1 ? " paper" : " papers") + " on " + day
                    + (shift.warnings().isEmpty() ? "" : ", with warnings recorded"));
        } catch (DateTimeParseException badTime) {
            flash.addFlashAttribute("error", "Use yyyy-mm-dd for the date and hh:mm for the time");
        } catch (IllegalArgumentException badInput) {
            flash.addFlashAttribute("error", badInput.getMessage());
        }
        return "redirect:/timings";
    }

    @PostMapping("/timings/revert")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String revert(@RequestParam String examId, Authentication authentication,
                         RedirectAttributes flash) {
        try {
            timings.revert(examId, actor.emailOf(authentication));
            flash.addFlashAttribute("notice", examId + " is back on its declared timing");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/timings";
    }

    /** Turns the three optional form fields into an override, refusing what cannot be parsed. */
    private static TimingOverride proposalOf(String examId, String date, String start, Integer minutes) {
        try {
            LocalDate parsedDate = date == null || date.isBlank() ? null : LocalDate.parse(date.trim());
            LocalTime parsedStart = start == null || start.isBlank() ? null : LocalTime.parse(start.trim());
            TimingOverride proposal = new TimingOverride(examId, parsedDate, parsedStart, minutes);
            if (!proposal.changesAnything()) {
                throw new IllegalArgumentException("Give a new date, start time or length");
            }
            return proposal;
        } catch (DateTimeParseException badTime) {
            throw new IllegalArgumentException("Use yyyy-mm-dd for the date and hh:mm for the time");
        }
    }

    /** Exposed for the template's "nothing to show" state, so it need not guess. */
    public List<TimingWorkflow.PaperTiming> papers() {
        return timings.papers();
    }
}
