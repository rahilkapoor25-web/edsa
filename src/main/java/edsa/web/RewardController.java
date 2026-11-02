package edsa.web;

import edsa.core.Assignment;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The substitution board and the points ledger.
 *
 * <p>Two pages over one workflow. {@code /substitutions} is where a duty is offered, taken,
 * approved and confirmed done; {@code /points} is where the consequences are visible — what
 * everybody has earned, what they have spent, and what they have asked to spend it on.
 *
 * <p>Permission is enforced in {@link RewardWorkflow} and not by what this page chooses to draw.
 * The buttons are hidden from people who may not use them because showing a control that will be
 * refused is poor manners, not because hiding it is the protection: every action re-checks the
 * signed-in account's own faculty id, so posting the request by hand gets the same refusal.
 */
@Controller
public class RewardController {

    private final Workspace workspace;
    private final RewardWorkflow rewards;
    private final SignedInActor actor;

    public RewardController(Workspace workspace, RewardWorkflow rewards, SignedInActor actor) {
        this.workspace = workspace;
        this.rewards = rewards;
        this.actor = actor;
    }

    /** A duty the signed-in professor holds, and whether it can be offered or redeemed. */
    public record OwnDuty(ExamSlot slot, String roomId, boolean alreadyOffered) {
    }

    @GetMapping("/substitutions")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'PROFESSOR')")
    public String board(Authentication authentication, Model model,
                        @RequestParam(required = false) String filter) {
        RewardWorkflow.Actor who = actor.rewardActor(authentication);
        // Offers whose sitting has been and gone are closed off on the way in, so the board is
        // not cluttered with decisions nobody can usefully take any more.
        rewards.tidy(who.email() == null ? "system" : who.email());

        model.addAttribute("hasPlan", workspace.hasPlan());
        model.addAttribute("isAdmin", who.administrator());
        model.addAttribute("ownFacultyId", who.facultyId());
        model.addAttribute("rules", rewards.rules());
        model.addAttribute("filter", filter == null ? "open" : filter);

        model.addAttribute("open", rewards.openOffers());
        model.addAttribute("awaitingApproval", rewards.awaitingApproval());
        model.addAttribute("awaitingCompletion", rewards.awaitingCompletion());
        model.addAttribute("mine", rewards.substitutionsFor(who.facultyId()));
        model.addAttribute("all", rewards.allSubstitutions());
        model.addAttribute("ownDuties", ownDuties(who.facultyId()));
        if (who.facultyId() != null) {
            model.addAttribute("balance", rewards.balanceOf(who.facultyId()));
        }
        if (workspace.hasData()) {
            model.addAttribute("faculty", workspace.data().faculty());
        }
        return "substitutions";
    }

    /**
     * The duties this professor holds in the current plan.
     *
     * <p>Empty for an account with no faculty record — an administrator who is not themselves a
     * professor has no duties to offer, and the page says so rather than offering a form that
     * could not work.
     */
    private List<OwnDuty> ownDuties(String facultyId) {
        if (facultyId == null || !workspace.hasPlan()) {
            return List.of();
        }
        ExamPlan plan = workspace.result().plan();
        List<OwnDuty> own = new ArrayList<>();
        for (Assignment duty : plan.duties().forFaculty(facultyId)) {
            ExamSlot slot = plan.slot(duty.slotId());
            if (slot == null) {
                continue;
            }
            boolean offered = rewards.allSubstitutions().stream()
                    .anyMatch(request -> request.slotId().equals(duty.slotId())
                            && request.roomId().equals(duty.roomId())
                            && request.status().isOpen());
            own.add(new OwnDuty(slot, duty.roomId(), offered));
        }
        return own;
    }

    @PostMapping("/substitutions/offer")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'PROFESSOR')")
    public String offer(@RequestParam String slotId, @RequestParam String roomId,
                        @RequestParam(required = false) String forFacultyId,
                        @RequestParam(required = false) String reason,
                        Authentication authentication, RedirectAttributes flash) {
        try {
            rewards.offer(actor.rewardActor(authentication), slotId, roomId, forFacultyId, reason);
            flash.addFlashAttribute("notice", "Offered room " + roomId + " for somebody to cover");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/substitutions";
    }

    @PostMapping("/substitutions/{id}/volunteer")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'PROFESSOR')")
    public String volunteer(@PathVariable long id, Authentication authentication,
                            RedirectAttributes flash) {
        try {
            rewards.volunteer(actor.rewardActor(authentication), id);
            flash.addFlashAttribute("notice", "Offered to cover it. An administrator has to approve it.");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/substitutions";
    }

    @PostMapping("/substitutions/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public String approve(@PathVariable long id, Authentication authentication, RedirectAttributes flash) {
        try {
            rewards.approveSubstitution(actor.rewardActor(authentication), id);
            flash.addFlashAttribute("notice", "Approved. No points are awarded until the duty is confirmed done.");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/substitutions";
    }

    @PostMapping("/substitutions/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public String reject(@PathVariable long id, @RequestParam(required = false) String why,
                         Authentication authentication, RedirectAttributes flash) {
        try {
            rewards.rejectSubstitution(actor.rewardActor(authentication), id, why);
            flash.addFlashAttribute("notice", "Rejected. The duty stays where it was.");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/substitutions";
    }

    @PostMapping("/substitutions/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public String complete(@PathVariable long id, Authentication authentication, RedirectAttributes flash) {
        try {
            int awarded = rewards.completeAndAward(actor.rewardActor(authentication), id);
            flash.addFlashAttribute("notice", awarded > 0
                    ? "Confirmed done and awarded " + awarded + " points"
                    : "Confirmed done. It had already been credited, so no second award was made.");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/substitutions";
    }

    @PostMapping("/substitutions/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'PROFESSOR')")
    public String withdraw(@PathVariable long id, Authentication authentication, RedirectAttributes flash) {
        try {
            rewards.withdraw(actor.rewardActor(authentication), id);
            flash.addFlashAttribute("notice", "Took the offer back");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/substitutions";
    }

    // ----- points -------------------------------------------------------------------

    @GetMapping("/points")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'PROFESSOR')")
    public String points(Authentication authentication, Model model) {
        RewardWorkflow.Actor who = actor.rewardActor(authentication);
        model.addAttribute("isAdmin", who.administrator());
        model.addAttribute("ownFacultyId", who.facultyId());
        model.addAttribute("rules", rewards.rules());
        model.addAttribute("hasPlan", workspace.hasPlan());

        if (who.facultyId() != null) {
            model.addAttribute("balance", rewards.balanceOf(who.facultyId()));
            model.addAttribute("ledger", rewards.ledgerFor(who.facultyId()));
            model.addAttribute("myRedemptions", rewards.redemptionsFor(who.facultyId()));
            model.addAttribute("ownDuties", ownDuties(who.facultyId()));
        }
        if (who.administrator()) {
            model.addAttribute("balances", rewards.allBalances());
            model.addAttribute("awaiting", rewards.redemptionsAwaiting());
            model.addAttribute("allRedemptions", rewards.allRedemptions());
            if (workspace.hasData()) {
                model.addAttribute("faculty", workspace.data().faculty());
            }
        }
        return "points";
    }

    @PostMapping("/points/redeem")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'PROFESSOR')")
    public String redeem(@RequestParam String slotId, @RequestParam String roomId,
                         @RequestParam(required = false) String reason,
                         Authentication authentication, RedirectAttributes flash) {
        try {
            rewards.requestRelief(actor.rewardActor(authentication), slotId, roomId, reason);
            flash.addFlashAttribute("notice", "Asked to be excused that duty. "
                    + "The points are held until an administrator decides.");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/points";
    }

    @PostMapping("/points/redemptions/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public String approveRelief(@PathVariable long id, Authentication authentication,
                                RedirectAttributes flash) {
        try {
            rewards.approveRelief(actor.rewardActor(authentication), id);
            flash.addFlashAttribute("notice", "Granted. The points are spent and a colleague has the duty.");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/points";
    }

    @PostMapping("/points/redemptions/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public String rejectRelief(@PathVariable long id, @RequestParam(required = false) String why,
                               Authentication authentication, RedirectAttributes flash) {
        try {
            rewards.rejectRelief(actor.rewardActor(authentication), id, why);
            flash.addFlashAttribute("notice", "Refused. The held points are back in the balance.");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/points";
    }

    @PostMapping("/points/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public String adjust(@RequestParam String facultyId, @RequestParam int points,
                         @RequestParam String why, Authentication authentication,
                         RedirectAttributes flash) {
        try {
            rewards.adjust(actor.rewardActor(authentication), facultyId, points, why);
            flash.addFlashAttribute("notice", "Corrected " + facultyId + " by "
                    + (points > 0 ? "+" : "") + points + " points");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/points";
    }
}
