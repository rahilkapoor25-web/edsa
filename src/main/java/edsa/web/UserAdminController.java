package edsa.web;

import edsa.auth.AccountService;

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
 * their account here. An account is switched off rather than deleted, so the duty history
 * it is attached to still makes sense.
 */
@Controller
public class UserAdminController {

    private final AccountService accounts;

    public UserAdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String page(Model model) {
        model.addAttribute("accounts", accounts.all());
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
}
