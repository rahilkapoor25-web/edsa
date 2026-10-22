package edsa.data;

/**
 * An imported faculty member as the Accounts page needs them: who they are, and where to
 * reach them.
 *
 * <p>Deliberately not {@code edsa.core.Faculty}. The core plans examinations and has no use
 * for a contact address; this record exists so an account can be tied to the faculty id that
 * came out of the workbook.
 */
public record FacultyContact(String id, String name, String email, String department) {

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
