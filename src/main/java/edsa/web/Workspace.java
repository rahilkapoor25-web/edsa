package edsa.web;

import edsa.core.DutyDecision;
import edsa.core.DutyEligibility;
import edsa.core.DutyPolicy;
import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.ExamPlanner;
import edsa.core.LeaveCalendar;
import edsa.core.LeaveRequest;
import edsa.core.LeaveStatus;
import edsa.core.PlanningResult;
import edsa.core.RepairResult;
import edsa.core.SchedulingConfig;
import edsa.core.SeniorityLadder;
import edsa.core.TimingOverrides;
import edsa.data.DutyDecisionRepository;
import edsa.data.LeaveRequestRepository;
import edsa.data.TimingRepository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * The data the coordinator loaded, the mode they chose, and the plan computed from the two.
 *
 * <p>One workspace for the whole application, as one examination office plans one session at a
 * time. Changing the mode or the data drops the plan built from the previous pair, so a stale
 * timetable is never left on screen: the next page load recomputes it.
 *
 * <p>Three things stored outside the workspace are folded into every compute, and the order
 * matters. Saved timing overrides are applied to the data <em>before</em> planning, because they
 * change when papers run and so change everything downstream. Confirmed leave goes into the
 * eligibility the allocator uses, so somebody away is never given a duty in the first place.
 * Coordinators' duty decisions are applied <em>after</em>, over the recommendations, because
 * they are decisions about a plan rather than inputs to one. Recomputing therefore refreshes the
 * recommendations without discarding anybody's approvals.
 */
@Component
public class Workspace {

    private final SchedulingConfig config;
    private final SeniorityLadder ladder;
    private final DutyPolicy policy;
    private final LeaveRequestRepository leaveRequests;
    private final TimingRepository timings;
    private final DutyDecisionRepository decisions;

    private ExamData data;
    private ExamMode mode = ExamMode.ENDSEM;
    private PlanningResult result;
    private RepairResult lastRepair;

    public Workspace(SchedulingConfig config, SeniorityLadder ladder, DutyPolicy policy,
                     LeaveRequestRepository leaveRequests, TimingRepository timings,
                     DutyDecisionRepository decisions) {
        this.config = config;
        this.ladder = ladder;
        this.policy = policy;
        this.leaveRequests = leaveRequests;
        this.timings = timings;
        this.decisions = decisions;
    }

    /** Loading data plans it straight away, so exams arrive already scheduled and staffed. */
    public synchronized void setData(ExamData data) {
        this.data = data;
        this.result = data == null ? null : computeNow();
        this.lastRepair = null;
    }

    /** Changing the mode re-times the whole session rather than leaving a stale plan. */
    public synchronized void setMode(ExamMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.result = data == null ? null : computeNow();
            this.lastRepair = null;
        }
    }

    public synchronized ExamMode mode() {
        return mode;
    }

    /** The imported data as it was loaded, before any timing override. */
    public synchronized ExamData data() {
        return data;
    }

    /** The data the current plan was built from: the imported data, re-timed. */
    public synchronized ExamData effectiveData() {
        return data == null ? null : overridesInForce().applyTo(data);
    }

    public synchronized boolean hasData() {
        return data != null;
    }

    public synchronized boolean hasPlan() {
        return result != null;
    }

    public synchronized SchedulingConfig config() {
        return config;
    }

    public SeniorityLadder ladder() {
        return ladder;
    }

    public DutyPolicy policy() {
        return policy;
    }

    public synchronized TimingOverrides overridesInForce() {
        return timings.inForce();
    }

    /**
     * The eligibility rules as they stand right now, including whoever is on confirmed leave.
     *
     * <p>Built fresh each time rather than held, because confirming a leave request has to change
     * the next plan. A cached calendar would mean an absence approved this morning was still
     * invisible to this afternoon's recompute.
     */
    public synchronized DutyEligibility eligibility() {
        LeaveCalendar calendar = LeaveCalendar.empty();
        for (LeaveRequest request : leaveRequests.withStatus(LeaveStatus.CONFIRMED)) {
            calendar.add(request.facultyId(), request.window());
        }
        return new DutyEligibility(ladder, policy, calendar);
    }

    /** A planner wired with the rules in force now, for this compute and no other. */
    public synchronized ExamPlanner planner() {
        return new ExamPlanner(config, eligibility());
    }

    /** Plans the loaded data under the current mode, replacing any earlier plan and repair. */
    public synchronized PlanningResult compute() {
        result = computeNow();
        lastRepair = null;
        return result;
    }

    private PlanningResult computeNow() {
        PlanningResult planned = planner().plan(overridesInForce().applyTo(data), mode);
        List<DutyDecision> saved = decisions.all();
        if (!saved.isEmpty()) {
            planned.plan().duties().applyAll(saved);
            // Re-checked after the decisions are laid over the recommendations: a coordinator
            // may have put somebody in a room who breaks a rule, and the page has to say so
            // rather than show a plan that was only legal before they touched it.
            planned = new PlanningResult(planned.plan(), planned.schedulingProblems(),
                    planned.allocationProblems(), planner().check(planned.plan()));
        }
        return planned;
    }

    /**
     * Puts a repaired roster in place of the current one and re-checks it, so the pages and
     * the downloads show the plan as it now stands rather than as it was first computed.
     */
    public synchronized void applyRepair(RepairResult repair) {
        List<String> allocationProblems = new ArrayList<>(result.allocationProblems());
        allocationProblems.addAll(repair.uncovered());
        result = new PlanningResult(repair.plan(), result.schedulingProblems(),
                List.copyOf(allocationProblems), planner().check(repair.plan()));
        lastRepair = repair;
    }

    public synchronized RepairResult lastRepair() {
        return lastRepair;
    }

    public synchronized PlanningResult result() {
        return result;
    }
}
