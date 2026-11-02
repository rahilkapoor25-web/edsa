package edsa.core;

/** Where a request to be excused a duty has got to. */
public enum RedemptionStatus {

    /** Asked for. The points are held but not yet spent, and the duty has not moved. */
    PENDING("Awaiting approval", "pending"),

    /** Granted: the points are spent and somebody else has the duty. */
    APPROVED("Approved", "ok"),

    /** Refused. The points are released back to the balance. */
    REJECTED("Rejected", "bad");

    private final String label;
    private final String tone;

    RedemptionStatus(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    public String tone() {
        return tone;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public static RedemptionStatus of(String name) {
        for (RedemptionStatus status : values()) {
            if (status.name().equalsIgnoreCase(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown redemption status: " + name);
    }
}
