package edsa.web;

import edsa.core.Absence;
import edsa.core.AbsenceWindow;
import edsa.core.ExamPlan;
import edsa.core.Faculty;
import edsa.core.LeaveRequest;
import edsa.core.LeaveStatus;
import edsa.core.RepairResult;
import edsa.core.AbsenceRepairer;
import edsa.data.LeaveRequestRepository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives a leave request from recorded, through reviewed, to covered.
 *
 * <p>This holds no rule of its own. Whether a request may move a duty is
 * {@link LeaveRequest#mayReassign()}; which duties an absence touches is
 * {@link AbsenceWindow#covers}; who takes a vacated room is {@link AbsenceRepairer}. All three
 * live in {@code edsa.core}. What happens here is the sequencing the pages need, which is why
 * it sits beside {@link Workspace} rather than inside a controller: a controller binds the
 * request and picks a view, and decides nothing.
 *
 * <p><strong>Nothing is automatic.</strong> Recording an absence changes no duty. Rejecting one
 * changes no duty. A deadline passing changes no duty — it only marks the request as overdue
 * for review, because silence is not approval. Duties move at exactly one moment: when an
 * administrator confirms.
 */
@Component
public class LeaveWorkflow {

    private final LeaveRequestRepository requests;
    private final Workspace workspace;
    private final LeaveProperties properties;
    private final AbsenceRepairer repairer = new AbsenceRepairer();

    public LeaveWorkflow(LeaveRequestRepository requests, Workspace workspace, LeaveProperties properties) {
        this.requests = requests;
        this.workspace = workspace;
        this.properties = properties;
    }

    /** The default review deadline for a request recorded now. */
    public LocalDateTime defaultDeadline() {
        return LocalDateTime.now().plusHours(properties.getConfirmWithinHours());
    }

    public List<LeaveRequest> requests() {
        return requests.all();
    }

    public List<LeaveRequest> awaitingReview() {
        return requests.withStatus(LeaveStatus.PENDING);
    }

    /**
     * Records an absence as pending. The duty roster is not touched.
     *
     * @param confirmBy the review deadline, or null for the configured default
     * @throws IllegalArgumentException when the professor is not one of the loaded invigilators
     */
    public LeaveRequest record(String facultyId, AbsenceWindow window, String reason, LocalDateTime confirmBy) {
        Faculty member = workspace.hasData() ? workspace.data().facultyMember(facultyId) : null;
        if (member == null) {
            throw new IllegalArgumentException("There is no invigilator with id " + facultyId
                    + " in the loaded data");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = confirmBy == null ? defaultDeadline() : confirmBy;
        if (!deadline.isAfter(now)) {
            throw new IllegalArgumentException("The confirmation deadline must be in the future");
        }
        return requests.record(LeaveRequest.pending(facultyId, member.getName(), window,
                blankToNull(reason), now, deadline));
    }

    /**
     * Turns a request down. Nothing is reassigned, exactly as while it was pending.
     *
     * @throws IllegalArgumentException when the request is unknown or already decided
     */
    @Transactional
    public LeaveRequest reject(long id, String decidedBy) {
        LeaveRequest request = mustFind(id);
        requireOpen(request);
        if (!requests.moveStatus(id, LeaveStatus.PENDING, LeaveStatus.REJECTED,
                LocalDateTime.now(), decidedBy, "Rejected. The duty roster was not changed.")) {
            throw new IllegalArgumentException("That request has already been decided");
        }
        return mustFind(id);
    }

    /**
     * Confirms a request and covers the duties it affects.
     *
     * <p>Everything happens in one transaction, in one order: the repair is computed first
     * against an untouched plan, the request is then moved to its new status, and the repaired
     * roster is adopted last. {@link AbsenceRepairer} builds a whole new roster rather than
     * editing the live one, so the plan either changes completely or not at all — there is no
     * state in which some duties moved and others silently did not. If anything fails, the
     * status change rolls back and the roster is never swapped.
     *
     * @throws IllegalArgumentException when the request is unknown or already decided
     */
    @Transactional
    public LeaveRequest confirmAndReassign(long id, String decidedBy) {
        LeaveRequest request = mustFind(id);
        requireOpen(request);

        if (!workspace.hasPlan()) {
            if (!requests.moveStatus(id, LeaveStatus.PENDING, LeaveStatus.CONFIRMED, LocalDateTime.now(),
                    decidedBy, "Confirmed. There is no computed plan yet, so no duty needed covering.")) {
                throw new IllegalArgumentException("That request has already been decided");
            }
            return mustFind(id);
        }

        ExamPlan plan = workspace.result().plan();
        RepairResult repair = repairer.repair(plan, request.facultyId(), request.window(),
                othersAwayThan(request.facultyId()));

        LeaveStatus outcome = repair.changedAnything() ? LeaveStatus.COMPLETED : LeaveStatus.CONFIRMED;
        if (!requests.moveStatus(id, LeaveStatus.PENDING, outcome, LocalDateTime.now(),
                decidedBy, summarise(repair))) {
            throw new IllegalArgumentException("That request has already been decided");
        }

        if (repair.changedAnything() || !repair.isComplete()) {
            workspace.applyRepair(repair);
        }
        return mustFind(id);
    }

    /** Everyone else already known to be away, so a room is not handed to another absentee. */
    private List<Absence> othersAwayThan(String facultyId) {
        return requests.confirmedAbsences().stream()
                .filter(absence -> !absence.facultyId().equals(facultyId))
                .toList();
    }

    private static String summarise(RepairResult repair) {
        StringBuilder outcome = new StringBuilder("Confirmed. ")
                .append(repair.changes().size())
                .append(repair.changes().size() == 1 ? " duty reassigned" : " duties reassigned");
        if (!repair.isComplete()) {
            outcome.append(", ").append(repair.uncovered().size()).append(" left uncovered");
        }
        return outcome.append('.').toString();
    }

    private LeaveRequest mustFind(long id) {
        return requests.find(id)
                .orElseThrow(() -> new IllegalArgumentException("There is no leave request numbered " + id));
    }

    private static void requireOpen(LeaveRequest request) {
        if (request.status().isDecided()) {
            throw new IllegalArgumentException("That request was already "
                    + request.status().label().toLowerCase() + ", so it cannot be decided again");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
