package edsa.auth;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Creates accounts, hashing the password before it reaches the database. */
@Service
public class AccountService {

    /** Deliberately permissive but structural: a local part, an @, a dotted domain. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AccountService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public static boolean isWellFormed(String email) {
        return email != null && EMAIL.matcher(email.trim()).matches() && email.trim().length() <= 254;
    }

    /**
     * Registers an account. The password is hashed here and the plaintext is never stored.
     *
     * @throws IllegalArgumentException when the address is malformed, the password is too
     *                                  short, or the address is already registered
     */
    public void register(String email, String rawPassword, String displayName, String role) {
        if (!isWellFormed(email)) {
            throw new IllegalArgumentException("'" + email + "' is not a valid email address");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("the password must be at least 8 characters");
        }
        if (users.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("an account already exists for that address");
        }
        users.create(email, encoder.encode(rawPassword), displayName, role, true);
    }

    /** What {@link #provisionAdministrator} had to do. */
    public enum AdminOutcome {
        CREATED,
        PASSWORD_RESET
    }

    /**
     * Makes sure the configured administrator exists and can sign in.
     *
     * <p>Creating the first account only while the table is completely empty was too strict to
     * be useful: once any account existed — a professor provisioned from a workbook, say — there
     * was no way to add an administrator or to recover one whose password had been lost, and
     * with a database that survives restarts that would be permanent. So this creates the
     * account when it is missing and otherwise puts the existing one back in working order.
     *
     * <p>It stays entirely opt-in. Nothing calls it unless an email and a password were supplied
     * deliberately, there is no default password, and the documentation says to leave the
     * settings unset outside a development machine — because anybody who can set them could
     * take the administrator account over.
     *
     * @throws IllegalArgumentException when the address is malformed or the password too short
     */
    public AdminOutcome provisionAdministrator(String email, String rawPassword, String displayName) {
        if (!isWellFormed(email)) {
            throw new IllegalArgumentException("'" + email + "' is not a valid email address");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("the password must be at least 8 characters");
        }
        if (users.findByEmail(email).isEmpty()) {
            users.create(email, encoder.encode(rawPassword), displayName, AppUser.ADMIN, true);
            return AdminOutcome.CREATED;
        }
        users.resetAdministrator(email, encoder.encode(rawPassword));
        return AdminOutcome.PASSWORD_RESET;
    }

    public Optional<AppUser> find(String email) {
        return users.findByEmail(email);
    }

    public List<AppUser> all() {
        return users.findAll();
    }

    /**
     * Switches an account off, or back on.
     *
     * @throws IllegalArgumentException when it is the last active administrator, since nobody
     *                                  would be left who could switch it back
     */
    public void setActive(String email, boolean active) {
        AppUser user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("no account for " + email));
        if (!active && isLastActiveAdministrator(user)) {
            throw new IllegalArgumentException("this is the last active administrator");
        }
        users.setActive(user.email(), active);
    }

    private boolean isLastActiveAdministrator(AppUser user) {
        return user.active() && "ADMIN".equals(user.role())
                && users.findAll().stream()
                .filter(other -> other.active() && "ADMIN".equals(other.role()))
                .count() <= 1;
    }

    public long accountCount() {
        return users.count();
    }
}
