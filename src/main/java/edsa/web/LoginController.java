package edsa.web;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A placeholder gate, not authentication. Any address at the institute's domain is let in
 * with any password: nothing is verified, no account exists, and no password is ever checked.
 * It exists so the pages have someone to name and something to clear on the way out. The
 * missing security layer is recorded as a scope limitation in the SRS; replacing this with
 * a real login means replacing this class, not extending it.
 */
@Controller
public class LoginController {

    static final String USER_EMAIL = "userEmail";

    private static final String INSTITUTE_DOMAIN = "@thapar.edu";
    private static final String WRONG_DOMAIN = "Use your @thapar.edu account";
    private static final String NO_PASSWORD = "Enter your password";

    @GetMapping("/login")
    public String signInPage() {
        return "login";
    }

    @PostMapping("/login")
    public String signIn(@RequestParam String email, @RequestParam String password,
                         HttpSession session, Model model) {
        String address = email.trim().toLowerCase();

        if (!address.endsWith(INSTITUTE_DOMAIN) || address.length() == INSTITUTE_DOMAIN.length()) {
            return refused(model, email, WRONG_DOMAIN);
        }
        if (password.isBlank()) {
            return refused(model, email, NO_PASSWORD);
        }

        session.setAttribute(USER_EMAIL, address);
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String signOut(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private String refused(Model model, String typedEmail, String problem) {
        model.addAttribute("email", typedEmail);
        model.addAttribute("error", problem);
        return "login";
    }
}
