package edsa.web;

import edsa.core.AbsenceRepairer;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;
import edsa.core.RepairResult;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Somebody cannot attend. Take their duties off them, give each to a colleague who is free,
 * and show exactly what moved.
 */
@Controller
public class DisruptionController {

    private final Workspace workspace;
    private final AbsenceRepairer repairer = new AbsenceRepairer();

    public DisruptionController(Workspace workspace) {
        this.workspace = workspace;
    }

    @GetMapping("/disruption")
    public String page(Model model) {
        model.addAttribute("hasPlan", workspace.hasPlan());
        model.addAttribute("mode", workspace.mode());
        if (workspace.hasPlan()) {
            ExamPlan plan = workspace.result().plan();
            model.addAttribute("workloads", plan.workloads());
            model.addAttribute("dates", plan.schedule().stream()
                    .map(ExamSlot::getDate).distinct().sorted().toList());
            model.addAttribute("repair", workspace.lastRepair());
        }
        return "disruption";
    }

    @PostMapping("/disruption")
    public String markAbsent(@RequestParam String facultyId,
                             @RequestParam(required = false) String date,
                             RedirectAttributes flash) {
        if (!workspace.hasPlan()) {
            flash.addFlashAttribute("error", "There is no plan to repair yet");
            return "redirect:/disruption";
        }
        LocalDate absentOn;
        try {
            absentOn = date == null || date.isBlank() ? null : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            flash.addFlashAttribute("error", "'" + date + "' is not a date");
            return "redirect:/disruption";
        }

        RepairResult repair = repairer.repair(workspace.result().plan(), facultyId, absentOn);
        if (repair.absent() == null) {
            flash.addFlashAttribute("error", String.join(", ", repair.uncovered()));
            return "redirect:/disruption";
        }
        if (!repair.changedAnything() && repair.isComplete()) {
            flash.addFlashAttribute("notice", repair.absent().getName()
                    + " had no duties" + (absentOn == null ? "" : " on " + absentOn) + ", so nothing moved");
            return "redirect:/disruption";
        }

        workspace.applyRepair(repair);
        return "redirect:/disruption";
    }
}
