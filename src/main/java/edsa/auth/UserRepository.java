package edsa.auth;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Accounts, stored in the application database through plain JDBC. */
@Repository
public class UserRepository {

    private static final String COLUMNS = "id, email, password_hash, display_name, role, active, faculty_id";

    private static final RowMapper<AppUser> MAPPER = (rs, row) -> new AppUser(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getString("role"),
            rs.getBoolean("active"),
            rs.getString("faculty_id"));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Looks an account up by email, case-insensitively, as addresses are not case sensitive. */
    public Optional<AppUser> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM app_user WHERE LOWER(email) = ?",
                    MAPPER, email.trim().toLowerCase()));
        } catch (EmptyResultDataAccessException unknown) {
            return Optional.empty();
        }
    }

    /** Looks an account up by the faculty record it was provisioned from. */
    public Optional<AppUser> findByFacultyId(String facultyId) {
        if (facultyId == null || facultyId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM app_user WHERE faculty_id = ?", MAPPER, facultyId.trim()));
        } catch (EmptyResultDataAccessException unknown) {
            return Optional.empty();
        }
    }

    /** The faculty records that already have an account, so provisioning can skip them. */
    public Set<String> facultyIdsWithAccounts() {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT faculty_id FROM app_user WHERE faculty_id IS NOT NULL", String.class));
    }

    public List<AppUser> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_user ORDER BY email", MAPPER);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Creates an account from an already-hashed password.
     *
     * @throws DuplicateKeyException when the address is taken
     */
    public void create(String email, String passwordHash, String displayName, String role, boolean active) {
        jdbc.update("INSERT INTO app_user (email, password_hash, display_name, role, active) VALUES (?, ?, ?, ?, ?)",
                email.trim().toLowerCase(), passwordHash, displayName, role, active);
    }

    /**
     * Creates a professor's account that cannot yet be signed into.
     *
     * <p>It is stored disabled, with a password hash nobody holds the password for and the
     * hash of a single-use activation secret. Setting a password through the activation link
     * is what enables it.
     *
     * @throws DuplicateKeyException when the address or the faculty record is already taken
     */
    public void createAwaitingActivation(String email, String unusablePasswordHash, String displayName,
                                         String facultyId, String activationHash, LocalDateTime expiresAt) {
        jdbc.update("INSERT INTO app_user (email, password_hash, display_name, role, active, faculty_id, "
                        + "activation_hash, activation_expires_at) VALUES (?, ?, ?, ?, FALSE, ?, ?, ?)",
                email.trim().toLowerCase(), unusablePasswordHash, displayName, AppUser.PROFESSOR,
                facultyId.trim(), activationHash, Timestamp.valueOf(expiresAt));
    }

    /** The activation secret's hash and expiry for one account, for verifying a link. */
    Optional<PendingActivation> pendingActivation(long accountId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id, email, display_name, activation_hash, activation_expires_at "
                            + "FROM app_user WHERE id = ?",
                    (rs, row) -> new PendingActivation(rs.getLong("id"), rs.getString("email"),
                            rs.getString("display_name"), rs.getString("activation_hash"),
                            rs.getTimestamp("activation_expires_at") == null ? null
                                    : rs.getTimestamp("activation_expires_at").toLocalDateTime()),
                    accountId));
        } catch (EmptyResultDataAccessException unknown) {
            return Optional.empty();
        }
    }

    /**
     * Sets the password its owner chose and enables the account, clearing the activation
     * secret so the link cannot be used a second time.
     *
     * @return true when the account was still awaiting activation
     */
    boolean activate(long accountId, String passwordHash) {
        return jdbc.update("UPDATE app_user SET password_hash = ?, active = TRUE, activation_hash = NULL, "
                        + "activation_expires_at = NULL WHERE id = ? AND activation_hash IS NOT NULL",
                passwordHash, accountId) == 1;
    }

    /** Switches an account on or off without deleting it, so its history is kept. */
    public void setActive(String email, boolean active) {
        jdbc.update("UPDATE app_user SET active = ? WHERE LOWER(email) = ?", active, email.trim().toLowerCase());
    }
}
