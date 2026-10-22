package edsa.web;

import edsa.auth.AccountService;
import edsa.auth.ProfessorAccountService;
import edsa.auth.ProvisioningCandidate;
import edsa.auth.ProvisioningSummary;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Accounts, for administrators only.
 *
 * <p>There is no self-registration: a professor gets in because an administrator created
 * their account here, either one at a time or in bulk from the faculty records the workbook
 * import already stored. An account is switched off rather than deleted, so the duty history
 * it is attached to still makes sense.
 */
@Controller
public class UserAdminController {

    private final AccountService accounts;
    private final ProfessorAccountService professors;

    public UserAdminController(AccountService accounts, ProfessorAccountService professors) {
        this.accounts = accounts;
        this.professors = professors;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String page(Model model) {
        populate(model);
        return "users";
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String create(@RequestParam String email,
                         @RequestParam String displayName,
                         @RequestParam String password,
                         @RequestParam(defaultValue = "PROFESSOR") String role,
                         RedirectAttributes flash) {
        try {
            accounts.register(email, password, displayName, "ADMIN".equals(role) ? "ADMIN" : "PROFESSOR");
            flash.addFlashAttribute("notice", "Created an account for " + email.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    /**
     * Creates an account for every imported professor who has not got one.
     *
     * <p>The result is rendered straight back rather than redirected to, because it carries
     * the one-time activation links: a redirect would put them in the session, and they have
     * no business being stored anywhere. Refreshing the page re-posts harmlessly — everybody
     * created the first time is simply skipped the second.
     */
    @PostMapping("/users/professors")
    @PreAuthorize("hasRole('ADMIN')")
    public String createMissingProfessorAccounts(Model model) {
        ProvisioningSummary summary = professors.createMissingAccounts();
        populate(model);
        model.addAttribute("summary", summary);
        if (summary.didNothing()) {
            model.addAttribute("notice", summary.unusableCount() + summary.skippedCount() == 0
                    ? "There are no imported faculty records to create accounts from"
                    : "Every imported professor already has an account, or has no usable address");
        } else {
            model.addAttribute("notice", "Created " + summary.createdCount()
                    + (summary.createdCount() == 1 ? " account" : " accounts")
                    + ". Pass each activation link to its owner now: they are not shown again.");
        }
        return "users";
    }

    @PostMapping("/users/active")
    @PreAuthorize("hasRole('ADMIN')")
    public String setActive(@RequestParam String email, @RequestParam boolean active, RedirectAttributes flash) {
        try {
            accounts.setActive(email, active);
            flash.addFlashAttribute("notice", (active ? "Enabled " : "Disabled ") + email);
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    private void populate(Model model) {
        model.addAttribute("accounts", accounts.all());
        List<ProvisioningCandidate> candidates = professors.preview();
        model.addAttribute("candidates", candidates);
        model.addAttribute("eligibleCount", candidates.stream()
                .filter(ProvisioningCandidate::isEligible).count());
    }
}
