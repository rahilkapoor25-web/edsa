package edsa.auth;

/**
 * An account that may sign in.
 *
 * <p>Only the hash of the password is ever held; the password itself is never stored, logged
 * or returned. Authentication is deliberately separate from exam allocation: nothing in
 * {@code edsa.core} knows accounts exist.
 */
public record AppUser(long id, String email, String passwordHash, String displayName,
                      String role, boolean active) {
}
