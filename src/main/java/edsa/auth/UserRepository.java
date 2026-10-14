package edsa.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Accounts, stored in the application database through plain JDBC. */
@Repository
public class UserRepository {

    private static final RowMapper<AppUser> MAPPER = (rs, row) -> new AppUser(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getString("role"),
            rs.getBoolean("active"));

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
                    "SELECT id, email, password_hash, display_name, role, active FROM app_user WHERE LOWER(email) = ?",
                    MAPPER, email.trim().toLowerCase()));
        } catch (EmptyResultDataAccessException unknown) {
            return Optional.empty();
        }
    }

    public List<AppUser> findAll() {
        return jdbc.query("SELECT id, email, password_hash, display_name, role, active FROM app_user ORDER BY email",
                MAPPER);
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

    /** Switches an account on or off without deleting it, so its history is kept. */
    public void setActive(String email, boolean active) {
        jdbc.update("UPDATE app_user SET active = ? WHERE LOWER(email) = ?", active, email.trim().toLowerCase());
    }
}
