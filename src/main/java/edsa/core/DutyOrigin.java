package edsa.core;

/**
 * Where a duty came from: the allocator proposed it, or a person decided it.
 *
 * <p>The distinction is the point of having it. A recommendation is this application's opinion,
 * safe to throw away and recompute; an approved or hand-made duty is somebody's decision, and
 * recomputing over it without saying so would quietly undo work a coordinator did deliberately.
 * So every assignment carries where it came from, and the pages show recommendations and
 * decisions differently rather than presenting both as "the plan".
 */
public enum DutyOrigin {

    /** The allocator's proposal. Nobody has looked at it yet. */
    RECOMMENDED("Recommended"),

    /** A coordinator reviewed the allocator's proposal and approved it unchanged. */
    APPROVED("Approved"),

    /** A coordinator put this duty in themselves, in place of what was recommended. */
    MANUAL("Assigned by hand"),

    /** Put in place by the absence repairer, covering somebody on confirmed leave. */
    SUBSTITUTED("Substitute");

    private final String label;

    DutyOrigin(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Whether a person stands behind this duty, as opposed to the allocator alone. */
    public boolean decidedByPerson() {
        return this == APPROVED || this == MANUAL;
    }
}
