package edsa.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Shows the sign-in page.
 *
 * <p>Spring Security handles the POST; this only renders the form and the one message it is
 * safe to show. That message never says whether an address is registered — an unknown
 * account, a wrong password and a disabled account all read the same, and so does the
 * message shown while a run of failures is being made to wait.
 */
@Controller
public class LoginController {

    private static final String REFUSED = "Email or password is incorrect";

    /**
     * Shown after a run of failures. It says nothing about whether the address exists, and
     * the wait clears itself, so nobody is locked out.
     */
    private static final String PAUSED =
            "Too many sign-in attempts. Wait a minute and try again.";

    @GetMapping("/login")
    public String signInPage(@RequestParam(required = false) String error,
                             @RequestParam(required = false) String loggedOut,
                             @RequestParam(required = false) String throttled,
                             Model model) {
        if (error != null) {
            model.addAttribute("error", REFUSED);
        }
        if (throttled != null) {
            model.addAttribute("error", PAUSED);
        }
        if (loggedOut != null) {
            model.addAttribute("notice", "You have been signed out");
        }
        return "login";
    }
}
