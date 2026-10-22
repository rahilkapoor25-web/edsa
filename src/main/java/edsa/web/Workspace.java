package edsa.web;

import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.ExamPlanner;
import edsa.core.PlanningResult;
import edsa.core.RepairResult;
import edsa.core.SchedulingConfig;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * The data the coordinator loaded, the mode they chose, and the plan computed from the two.
 *
 * <p>One workspace for the whole application, as one examination office plans one session at a
 * time. Changing the mode or the data drops the plan built from the previous pair, so a stale
 * timetable is never left on screen: the next page load recomputes it.
 */
@Component
public class Workspace {

    private final ExamPlanner planner;

    private ExamData data;
    private ExamMode mode = ExamMode.ENDSEM;
    private PlanningResult result;
    private RepairResult lastRepair;

    public Workspace(ExamPlanner planner) {
        this.planner = planner;
    }

    /** Loading data plans it straight away, so exams arrive already scheduled and staffed. */
    public synchronized void setData(ExamData data) {
        this.data = data;
        this.result = data == null ? null : planner.plan(data, mode);
        this.lastRepair = null;
    }

    /** Changing the mode re-times the whole session rather than leaving a stale plan. */
    public synchronized void setMode(ExamMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.result = data == null ? null : planner.plan(data, mode);
            this.lastRepair = null;
        }
    }

    public synchronized ExamMode mode() {
        return mode;
    }

    public synchronized ExamData data() {
        return data;
    }

    public synchronized boolean hasData() {
        return data != null;
    }

    public synchronized boolean hasPlan() {
        return result != null;
    }

    public synchronized SchedulingConfig config() {
        return planner.config();
    }

    /** Plans the loaded data under the current mode, replacing any earlier plan and repair. */
    public synchronized PlanningResult compute() {
        result = planner.plan(data, mode);
        lastRepair = null;
        return result;
    }

    /**
     * Puts a repaired roster in place of the current one and re-checks it, so the pages and
     * the downloads show the plan as it now stands rather than as it was first computed.
     */
    public synchronized void applyRepair(RepairResult repair) {
        List<String> allocationProblems = new ArrayList<>(result.allocationProblems());
        allocationProblems.addAll(repair.uncovered());
        result = new PlanningResult(repair.plan(), result.schedulingProblems(),
                List.copyOf(allocationProblems), planner.check(repair.plan()));
        lastRepair = repair;
    }

    public synchronized RepairResult lastRepair() {
        return lastRepair;
    }

    public synchronized PlanningResult result() {
        return result;
    }
}
