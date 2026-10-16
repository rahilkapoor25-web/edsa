package edsa.auth;

/**
 * An account that may sign in.
 *
 * <p>Only the hash of the password is ever held; the password itself is never stored, logged
 * or returned. Authentication is deliberately separate from exam allocation: nothing in
 * {@code edsa.core} knows accounts exist.
 *
 * <p>{@code facultyId} ties the account to the imported faculty record it was provisioned
 * from, and is null for an account created by hand. It is the faculty id and never the name,
 * because an id is stable across imports and two people may share a name.
 *
 * <p>The single-use activation secret is deliberately absent from this record. It is read
 * only by the one query that verifies it, so a token hash cannot leak into a page that merely
 * lists accounts.
 */
public record AppUser(long id, String email, String passwordHash, String displayName,
                      String role, boolean active, String facultyId) {

    public static final String PROFESSOR = "PROFESSOR";
    public static final String ADMIN = "ADMIN";

    public boolean isAdministrator() {
        return ADMIN.equals(role);
    }

    /** True when this account came from a row of the Faculty worksheet. */
    public boolean isLinkedToFaculty() {
        return facultyId != null && !facultyId.isBlank();
    }
}
