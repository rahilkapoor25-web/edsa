package edsa.web;

import edsa.auth.ProfessorAccountService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Where a professor whose account was created for them sets their own password.
 *
 * <p>Reachable without signing in, because the whole point is that the account cannot be
 * signed into yet. The link's secret is the only credential: it is random, single use and
 * expiring, and it is never written to a log or echoed back into the page.
 */
@Controller
public class ActivationController {

    private final ProfessorAccountService professors;

    public ActivationController(ProfessorAccountService professors) {
        this.professors = professors;
    }

    @GetMapping("/activate")
    public String form(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("usable", professors.isActivatable(token));
        return "activate";
    }

    @PostMapping("/activate")
    public String setPassword(@RequestParam(required = false) String token,
                              @RequestParam(required = false) String password,
                              @RequestParam(required = false) String confirmation,
                              Model model) {
        model.addAttribute("token", token);
        try {
            if (password == null || !password.equals(confirmation)) {
                throw new IllegalArgumentException("The two passwords do not match");
            }
            String email = professors.activate(token, password);
            model.addAttribute("activated", email);
            model.addAttribute("usable", false);
            return "activate";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("usable", professors.isActivatable(token));
            return "activate";
        }
    }
}
