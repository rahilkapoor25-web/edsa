package edsa.core;

import java.util.List;

/** The plan the allocator built, and anything it could not place. */
public record AllocationResult(ExamPlan plan, List<String> problems) {
}
