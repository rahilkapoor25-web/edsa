package edsa.auth;

/**
 * An account that was just created, with the single-use link its owner activates it through.
 *
 * <p>The token is a cryptographically random secret. Only its hash was stored, so this is the
 * only time it exists in a readable form — once the page showing it is gone, the only way to
 * let somebody in is to provision them again.
 */
public record ProvisionedAccount(String facultyId, String name, String email, String activationToken) {
}
