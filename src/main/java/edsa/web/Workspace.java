package edsa.web;

import edsa.core.ExamData;
import edsa.core.ExamPlan;

import org.springframework.stereotype.Component;

/**
 * The data the coordinator loaded and the plan computed from it.
 *
 * <p>One workspace for the whole application, as one examination office works on one plan at a
 * time. Nothing is written to the database yet, so a restart loses it. Loading data drops any
 * plan built from the data before it, because that plan no longer describes what is loaded.
 */
@Component
public class Workspace {

    private ExamData data;
    private ExamPlan plan;

    public synchronized void setData(ExamData data) {
        this.data = data;
        this.plan = null;
    }

    public synchronized void setPlan(ExamPlan plan) {
        this.plan = plan;
    }

    public synchronized ExamData data() {
        return data;
    }

    public synchronized ExamPlan plan() {
        return plan;
    }

    public synchronized boolean hasData() {
        return data != null;
    }

    public synchronized boolean hasPlan() {
        return plan != null;
    }
}
