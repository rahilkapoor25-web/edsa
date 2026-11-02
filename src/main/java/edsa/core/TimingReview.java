package edsa.core;

import java.util.List;

/**
 * What a proposed timing change would do, worked out before it is saved.
 *
 * <p>Two kinds of finding, kept apart because they need different answers from the person
 * making the change. A blocker is a change that cannot be made at all — a length no mode
 * accepts, a sitting that would run after the hall closes — and no amount of confirming makes
 * it workable. A warning is a change that would work but would cost something: a student with
 * two papers at once, a room short of seats, no invigilator left free. Those are the
 * examination office's call, not this application's, so they are shown and then obeyed if the
 * office says so.
 *
 * @param blockers    reasons the change cannot be saved
 * @param warnings    what the change would break, in the office's own terms
 * @param resultingPlan the plan the change would produce, so the page can show it before saving
 */
public record TimingReview(List<String> blockers, List<String> warnings, PlanningResult resultingPlan) {

    public TimingReview {
        blockers = List.copyOf(blockers);
        warnings = List.copyOf(warnings);
    }

    public static TimingReview blocked(String reason) {
        return new TimingReview(List.of(reason), List.of(), null);
    }

    public boolean isBlocked() {
        return !blockers.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /** Whether the change may be saved without anybody confirming anything. */
    public boolean isClean() {
        return !isBlocked() && !hasWarnings();
    }

    /** Everything the page has to say about the change, blockers first. */
    public List<String> findings() {
        return java.util.stream.Stream.concat(blockers.stream(), warnings.stream()).toList();
    }
}
