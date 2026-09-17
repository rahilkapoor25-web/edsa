package edsa.core;

/** A seating plan and duty roster, together with the data they refer to. */
public final class ExamPlan {

    private final ExamData data;
    private final SeatingPlan seating;
    private final DutyRoster duties;

    public ExamPlan(ExamData data, SeatingPlan seating, DutyRoster duties) {
        this.data = data;
        this.seating = seating;
        this.duties = duties;
    }

    public ExamData data() {
        return data;
    }

    public SeatingPlan seating() {
        return seating;
    }

    public DutyRoster duties() {
        return duties;
    }
}
