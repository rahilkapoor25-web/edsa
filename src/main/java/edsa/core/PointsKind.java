package edsa.core;

/** Why points moved. Every row in the ledger is one of these. */
public enum PointsKind {

    /** A substitute duty was completed and credited. */
    EARNED("Earned", true),

    /** Points were spent being excused a future duty. */
    REDEEMED("Redeemed", false),

    /** Points were not used within their validity and lapsed. */
    EXPIRED("Expired", false),

    /** An administrator corrected the balance by hand, in either direction. */
    ADJUSTED("Adjusted", true);

    private final String label;
    private final boolean canBePositive;

    PointsKind(String label, boolean canBePositive) {
        this.label = label;
        this.canBePositive = canBePositive;
    }

    public String label() {
        return label;
    }

    public boolean canBePositive() {
        return canBePositive;
    }

    public static PointsKind of(String name) {
        for (PointsKind kind : values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown points kind: " + name);
    }
}
