package edsa.core;

import java.util.List;

/**
 * Plans one examination session end to end: schedule the papers, seat the students, staff the
 * rooms, then check the finished plan against every rule independently.
 *
 * <p>The check is deliberately separate from the building: the allocator avoids breaking rules
 * as it works, and the checker proves afterwards that it did.
 */
public final class ExamPlanner {

    private final SchedulingConfig config;
    private final TimetableScheduler scheduler;
    private final GreedyAllocator allocator;
    private final PlanChecker checker;

    private final DutyEligibility eligibility;

    public ExamPlanner(SchedulingConfig config) {
        this(config, new DutyEligibility(SeniorityLadder.defaults(), DutyPolicy.unweighted(),
                LeaveCalendar.empty()));
    }

    /**
     * Plans under a configured duty policy. Confirmed leave is part of the eligibility handed
     * in, so a planner built with today's leave will not staff a room with somebody who is away
     * — and a planner built before a leave request was confirmed knows nothing about it, which
     * is why the pages build a fresh one rather than holding on to this.
     */
    public ExamPlanner(SchedulingConfig config, DutyEligibility eligibility) {
        this.config = config;
        this.eligibility = eligibility;
        this.scheduler = new TimetableScheduler(config);
        this.allocator = new GreedyAllocator(config, eligibility);
        this.checker = PlanChecker.using(config);
    }

    public ExamPlanner() {
        this(SchedulingConfig.defaults());
    }

    public DutyEligibility eligibility() {
        return eligibility;
    }

    public SchedulingConfig config() {
        return config;
    }

    /** Re-checks a plan that was changed after it was built, such as by a repair. */
    public List<CheckResult> check(ExamPlan plan) {
        return checker.inspect(plan);
    }

    public PlanningResult plan(ExamData data, ExamMode mode) {
        SchedulingResult scheduled = scheduler.schedule(data, mode);
        AllocationResult allocated = allocator.allocate(data, mode, scheduled.timetable());
        return new PlanningResult(allocated.plan(), scheduled.problems(), allocated.problems(),
                checker.inspect(allocated.plan()));
    }
}
