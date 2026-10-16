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

        /** An account will be created for this one. */
        ELIGIBLE("Will be created"),

        /** Nothing to do: they already have an account. */
        SKIPPED("Already has an account"),

        /** Cannot be used: no address, a malformed one, or one another account holds. */
        UNUSABLE("Cannot be used");

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
                "No account yet");
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
