package edsa.core;

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

    public ExamPlanner(SchedulingConfig config) {
        this.config = config;
        this.scheduler = new TimetableScheduler(config);
        this.allocator = new GreedyAllocator(config);
        this.checker = PlanChecker.using(config);
    }

    public ExamPlanner() {
        this(SchedulingConfig.defaults());
    }

    public SchedulingConfig config() {
        return config;
    }

    public PlanningResult plan(ExamData data, ExamMode mode) {
        SchedulingResult scheduled = scheduler.schedule(data, mode);
        AllocationResult allocated = allocator.allocate(data, mode, scheduled.timetable());
        return new PlanningResult(allocated.plan(), scheduled.problems(), allocated.problems(),
                checker.inspect(allocated.plan()));
    }
}
