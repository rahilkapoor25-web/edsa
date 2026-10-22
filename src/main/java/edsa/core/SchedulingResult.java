package edsa.core;

import java.util.List;

/**
 * What the scheduler managed to place, and why anything it could not place was impossible.
 *
 * <p>An exam that cannot be fitted is reported rather than squeezed in: a plan that silently
 * drops a paper is worse than one that says which paper it could not seat.
 */
public record SchedulingResult(Timetable timetable, List<String> problems) {

    public boolean isComplete() {
        return problems.isEmpty();
    }
}
