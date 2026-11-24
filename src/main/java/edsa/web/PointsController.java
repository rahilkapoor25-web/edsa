package edsa.web;

import edsa.core.Faculty;
import edsa.core.Workload;
import edsa.data.DutyPointRepository;
import edsa.data.ExamDataRepository;
import edsa.data.FacultyContact;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Duty points for every teacher, and the history behind them. Administrators only.
 *
 * <p>The teachers listed are the loaded session's invigilators; when that session is the imported
 * workbook, its unavailable members and their emails are added from the stored records. Anybody
 * who earned points in an earlier session but is not listed now still gets a row, so no point
 * ever disappears from the page.
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class PointsController {

    /** One teacher's row. */
    public record TeacherPoints(String id, String name, String department, String post, String email,
                                boolean available, int duties, int covered, int missed) {

        public int points() {
            return covered - missed;
        }

        /** "+2", "0" or "−1", with a real minus sign. */
        public String signedPoints() {
            int points = points();
            return points > 0 ? "+" + points : points < 0 ? "−" + (-points) : "0";
        }
    }

    private final Workspace workspace;
    private final ExamDataRepository records;
    private final DutyPointRepository points;

    public PointsController(Workspace workspace, ExamDataRepository records, DutyPointRepository points) {
        this.workspace = workspace;
        this.records = records;
        this.points = points;
    }

    @GetMapping("/points")
    public String page(Model model) {
        List<DutyPointRepository.Entry> history = points.history();
        model.addAttribute("teachers", teachers());
        model.addAttribute("history", history);
        return "points";
    }

    private List<TeacherPoints> teachers() {
        Map<String, DutyPointRepository.Tally> tallies = points.tallies();
        Map<String, FacultyContact> onRecord = records.facultyContacts().stream()
                .collect(Collectors.toMap(FacultyContact::id, Function.identity(), (first, second) -> first));
        List<Faculty> inSession = workspace.hasData() ? workspace.data().faculty() : List.of();
        Map<String, Integer> duties = new HashMap<>();
        if (workspace.hasPlan()) {
            for (Workload workload : workspace.result().plan().workloads()) {
                duties.put(workload.member().getId(), workload.duties());
            }
        }
        // The stored records describe this session only when every invigilator in it matches one.
        boolean sessionIsOnRecord = inSession.stream().allMatch(member ->
                onRecord.containsKey(member.getId()) && onRecord.get(member.getId()).name().equals(member.getName()));

        List<TeacherPoints> rows = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        for (Faculty member : inSession) {
            FacultyContact contact = sessionIsOnRecord ? onRecord.get(member.getId()) : null;
            rows.add(row(member.getId(), member.getName(), member.getDepartment(), member.getPostLabel(),
                    contact == null ? null : contact.email(), true, duties, tallies));
            listed.add(member.getId());
        }
        if (sessionIsOnRecord) {
            for (FacultyContact contact : onRecord.values()) {
                if (listed.add(contact.id())) {
                    rows.add(row(contact.id(), contact.name(), contact.department(), null, contact.email(), false,
                            duties, tallies));
                }
            }
        }
        tallies.forEach((id, tally) -> {
            if (listed.add(id)) {
                rows.add(row(id, tally.facultyName(), null, null, null, false, duties, tallies));
            }
        });
        rows.sort(Comparator.comparingInt(TeacherPoints::points).reversed().thenComparing(TeacherPoints::id));
        return rows;
    }

    private static TeacherPoints row(String id, String name, String department, String post, String email,
                                     boolean available, Map<String, Integer> duties,
                                     Map<String, DutyPointRepository.Tally> tallies) {
        DutyPointRepository.Tally tally = tallies.get(id);
        return new TeacherPoints(id, name, department, post, email, available, duties.getOrDefault(id, 0),
                tally == null ? 0 : tally.covered(), tally == null ? 0 : tally.missed());
    }
}
