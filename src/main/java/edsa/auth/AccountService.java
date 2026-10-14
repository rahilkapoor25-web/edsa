package edsa.auth;

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

    public Optional<AppUser> find(String email) {
        return users.findByEmail(email);
    }

    public long accountCount() {
        return users.count();
    }
}
