package edsa.web;

import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.ExamPlanner;
import edsa.core.PlanningResult;
import edsa.core.SchedulingConfig;

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

    public Workspace(ExamPlanner planner) {
        this.planner = planner;
    }

    public synchronized void setData(ExamData data) {
        this.data = data;
        this.result = null;
    }

    public synchronized void setMode(ExamMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.result = null;
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

    /** Plans the loaded data under the current mode, replacing any earlier plan. */
    public synchronized PlanningResult compute() {
        result = planner.plan(data, mode);
        return result;
    }

    public synchronized PlanningResult result() {
        return result;
    }
}
