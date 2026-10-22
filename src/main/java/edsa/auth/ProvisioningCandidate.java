package edsa.auth;

/**
 * One imported faculty record, and whether an account can be made from it.
 *
 * <p>Every record is reported, including the ones nothing will happen to, so the
 * administrator sees why nine accounts were created from twelve rows rather than wondering.
 */
public record ProvisioningCandidate(String facultyId, String name, String email, String department,
                                    Eligibility eligibility, String reason) {

    public enum Eligibility {

        /** An account will be created for this one, and an invitation link produced. */
        ELIGIBLE("Ready to invite"),

        /** Nothing to do: they already have an account, which is left exactly as it is. */
        SKIPPED("Has an account"),

        /** No usable address: missing, malformed, an example domain, or already taken. */
        UNUSABLE("Needs a real address");

        private final String label;

        Eligibility(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static ProvisioningCandidate eligible(String facultyId, String name, String email, String department) {
        return new ProvisioningCandidate(facultyId, name, email, department, Eligibility.ELIGIBLE,
                "Will be sent an invitation link");
    }

    public static ProvisioningCandidate skipped(String facultyId, String name, String email,
                                                String department, String reason) {
        return new ProvisioningCandidate(facultyId, name, email, department, Eligibility.SKIPPED, reason);
    }

    public static ProvisioningCandidate unusable(String facultyId, String name, String email,
                                                 String department, String reason) {
        return new ProvisioningCandidate(facultyId, name, email, department, Eligibility.UNUSABLE, reason);
    }

    public boolean isEligible() {
        return eligibility == Eligibility.ELIGIBLE;
    }
}
