package edsa.auth;

import java.util.List;

/**
 * What a bulk creation did: the accounts made, the records left alone, and the records that
 * could not be used.
 *
 * <p>{@code created} carries each new account's one-time activation link. That link is
 * returned here and nowhere else: it is not stored, not logged, and not shown again, so the
 * page that renders this summary is the only chance to pass it on.
 */
public record ProvisioningSummary(List<ProvisionedAccount> created,
                                  List<ProvisioningCandidate> skipped,
                                  List<ProvisioningCandidate> unusable,
                                  List<String> failures) {

    public static ProvisioningSummary of(List<ProvisionedAccount> created,
                                         List<ProvisioningCandidate> skipped,
                                         List<ProvisioningCandidate> unusable,
                                         List<String> failures) {
        return new ProvisioningSummary(List.copyOf(created), List.copyOf(skipped),
                List.copyOf(unusable), List.copyOf(failures));
    }

    public int createdCount() {
        return created.size();
    }

    public int skippedCount() {
        return skipped.size();
    }

    public int unusableCount() {
        return unusable.size();
    }

    public boolean didNothing() {
        return created.isEmpty();
    }
}
