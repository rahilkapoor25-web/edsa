package edsa.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** How the duties fell across the faculty. */
@Controller
public class WorkloadsController {

    private final Workspace workspace;

    public WorkloadsController(Workspace workspace) {
        this.workspace = workspace;
    }

    @GetMapping("/workloads")
    public String page(Model model) {
        model.addAttribute("hasPlan", workspace.hasPlan());
        if (workspace.hasPlan()) {
            model.addAttribute("workloads", workspace.plan().workloads());
            model.addAttribute("dutyCount", workspace.plan().duties().all().size());
        }
        return "workloads";
    }
}
