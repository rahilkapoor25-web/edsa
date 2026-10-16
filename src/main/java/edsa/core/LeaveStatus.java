package edsa.core;

/**
 * Where a leave request stands.
 *
 * <p>The system never decides that somebody is ill. A request is recorded as {@link #PENDING}
 * and stays there, changing nothing, until an authorised person confirms or rejects it — and
 * only {@link #CONFIRMED} leave may move a duty.
 */
public enum LeaveStatus {

    /** Recorded, awaiting a human decision. The duty roster is untouched. */
    PENDING("Pending", "Awaiting confirmation"),

    /** Confirmed by an administrator. The affected duties may now be reassigned. */
    CONFIRMED("Confirmed", "Confirmed, reassignment due"),

    /** Turned down. The duty roster is untouched, exactly as while it was pending. */
    REJECTED("Rejected", "Rejected, roster unchanged"),

    /** Confirmed, and the affected duties have been reassigned. */
    COMPLETED("Completed", "Duties reassigned");

    private final String label;
    private final String explanation;

    LeaveStatus(String label, String explanation) {
        this.label = label;
        this.explanation = explanation;
    }

    public String label() {
        return label;
    }

    public String explanation() {
        return explanation;
    }

    /** Still awaiting a decision. */
    public boolean isOpen() {
        return this == PENDING;
    }

    /** Whether duties may be taken off this person on the strength of this request. */
    public boolean mayReassign() {
        return this == CONFIRMED;
    }

    /** True for everything except a pending request: a decision has been taken. */
    public boolean isDecided() {
        return this != PENDING;
    }

    public static LeaveStatus of(String name) {
        for (LeaveStatus status : values()) {
            if (status.name().equalsIgnoreCase(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown leave status '" + name + "'");
    }
}
