package edsa.core;

/**
 * Where a substitute-duty offer has got to.
 *
 * <p>The transitions are spelled out as methods rather than left to whoever happens to be
 * writing an update, because this is where the integrity of the points ledger actually lives: a
 * substitution that can be completed twice, or approved after it was rejected, is a way to mint
 * points. Each method answers one question — may this happen now? — and the service asks before
 * it writes.
 */
public enum SubstitutionStatus {

    /** Offered by the duty's owner. Anybody eligible may volunteer. */
    OFFERED("Awaiting a volunteer", "pending"),

    /** A colleague has volunteered. An administrator has still to approve it. */
    CLAIMED("Awaiting approval", "pending"),

    /** Approved: the duty has moved, and the substitute is expected to turn up. */
    APPROVED("Approved", "ok"),

    /** The substitute invigilated. This is the only state in which points are awarded. */
    COMPLETED("Completed", "ok"),

    /** An administrator refused it. The duty stays where it was. */
    REJECTED("Rejected", "bad"),

    /** The owner took the offer back before anybody approved it. */
    WITHDRAWN("Withdrawn", "muted"),

    /** The sitting came and went without the offer being approved. */
    EXPIRED("Expired", "muted");

    private final String label;
    private final String tone;

    SubstitutionStatus(String label, String tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    /** Which badge the page paints this with: ok, pending, bad or muted. */
    public String tone() {
        return tone;
    }

    /** Whether the offer still holds the duty's sitting, so no second offer may be made for it. */
    public boolean isOpen() {
        return this == OFFERED || this == CLAIMED || this == APPROVED;
    }

    public boolean isFinished() {
        return !isOpen();
    }

    public boolean mayVolunteer() {
        return this == OFFERED;
    }

    /** Only a claimed offer may be approved or rejected: there has to be somebody to approve. */
    public boolean mayDecide() {
        return this == CLAIMED;
    }

    /** Only an approved substitution may be marked done, and only once. */
    public boolean mayComplete() {
        return this == APPROVED;
    }

    /** The owner may take back an offer nobody has approved yet. */
    public boolean mayWithdraw() {
        return this == OFFERED || this == CLAIMED;
    }

    public boolean earnsPoints() {
        return this == COMPLETED;
    }

    public static SubstitutionStatus of(String name) {
        for (SubstitutionStatus status : values()) {
            if (status.name().equalsIgnoreCase(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown substitution status: " + name);
    }
}
