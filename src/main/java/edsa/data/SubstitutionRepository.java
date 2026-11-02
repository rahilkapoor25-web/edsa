package edsa.data;

import edsa.core.SubstitutionRequest;
import edsa.core.SubstitutionStatus;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Substitute-duty offers, stored through plain JDBC.
 *
 * <p>Every state change here is a conditional update — {@code SET status = ? WHERE id = ? AND
 * status = ?} — and the caller is told how many rows it touched. That is what makes the
 * lifecycle safe against two people acting at once: the second volunteer's update matches no
 * row, so they are told the duty is taken rather than quietly overwriting the first. Checking
 * the status in Java first and updating afterwards would leave exactly that gap open.
 */
@Repository
public class SubstitutionRepository {

    private static final String COLUMNS = "id, slot_id, room_id, paper_code, starts_at, ends_at, "
            + "original_faculty_id, reason, status, offered_at, offered_by, volunteer_faculty_id, "
            + "volunteered_at, decided_by, decided_at, completed_by, completed_at, outcome";

    private static final RowMapper<SubstitutionRequest> MAPPER = (rs, row) -> new SubstitutionRequest(
            rs.getLong("id"),
            rs.getString("slot_id"),
            rs.getString("room_id"),
            rs.getString("paper_code"),
            rs.getTimestamp("starts_at").toLocalDateTime(),
            rs.getTimestamp("ends_at").toLocalDateTime(),
            rs.getString("original_faculty_id"),
            rs.getString("reason"),
            SubstitutionStatus.of(rs.getString("status")),
            rs.getTimestamp("offered_at").toLocalDateTime(),
            rs.getString("offered_by"),
            rs.getString("volunteer_faculty_id"),
            rs.getTimestamp("volunteered_at") == null ? null : rs.getTimestamp("volunteered_at").toLocalDateTime(),
            rs.getString("decided_by"),
            rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toLocalDateTime(),
            rs.getString("completed_by"),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
            rs.getString("outcome"));

    private final JdbcTemplate jdbc;

    public SubstitutionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SubstitutionRequest offer(SubstitutionRequest request) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO substitution_request (slot_id, room_id, paper_code, starts_at, ends_at, "
                            + "original_faculty_id, reason, status, offered_at, offered_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, request.slotId());
            statement.setString(2, request.roomId());
            statement.setString(3, request.paperCode());
            statement.setTimestamp(4, Timestamp.valueOf(request.startsAt()));
            statement.setTimestamp(5, Timestamp.valueOf(request.endsAt()));
            statement.setString(6, request.originalFacultyId());
            statement.setString(7, request.reason());
            statement.setString(8, request.status().name());
            statement.setTimestamp(9, Timestamp.valueOf(request.offeredAt()));
            statement.setString(10, request.offeredBy());
            return statement;
        }, keys);
        Number id = keys.getKey();
        return find(id == null ? 0 : id.longValue())
                .orElseThrow(() -> new IllegalStateException("the substitution offer was not stored"));
    }

    public Optional<SubstitutionRequest> find(long id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM substitution_request WHERE id = ?", MAPPER, id));
        } catch (EmptyResultDataAccessException unknown) {
            return Optional.empty();
        }
    }

    public List<SubstitutionRequest> all() {
        return jdbc.query("SELECT " + COLUMNS + " FROM substitution_request "
                + "ORDER BY starts_at, id", MAPPER);
    }

    public List<SubstitutionRequest> withStatus(SubstitutionStatus status) {
        return jdbc.query("SELECT " + COLUMNS + " FROM substitution_request WHERE status = ? "
                + "ORDER BY starts_at, id", MAPPER, status.name());
    }

    /** Whether this room-sitting already has an offer nobody has finished with. */
    public boolean hasOpenOffer(String slotId, String roomId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM substitution_request WHERE slot_id = ? AND room_id = ? "
                        + "AND status IN ('OFFERED', 'CLAIMED', 'APPROVED')", Integer.class, slotId, roomId);
        return count != null && count > 0;
    }

    /**
     * Records a volunteer, but only while the offer is still open for one.
     *
     * @return true when this caller got it; false when somebody else did first
     */
    public boolean claim(long id, String volunteerFacultyId, LocalDateTime now) {
        return jdbc.update("UPDATE substitution_request SET status = ?, volunteer_faculty_id = ?, "
                        + "volunteered_at = ? WHERE id = ? AND status = ?",
                SubstitutionStatus.CLAIMED.name(), volunteerFacultyId, Timestamp.valueOf(now),
                id, SubstitutionStatus.OFFERED.name()) == 1;
    }

    /** Approves or rejects a claimed offer. False when it was no longer claimed. */
    public boolean decide(long id, SubstitutionStatus outcome, String decidedBy,
                          LocalDateTime now, String note) {
        return jdbc.update("UPDATE substitution_request SET status = ?, decided_by = ?, decided_at = ?, "
                        + "outcome = ? WHERE id = ? AND status = ?",
                outcome.name(), decidedBy, Timestamp.valueOf(now), note,
                id, SubstitutionStatus.CLAIMED.name()) == 1;
    }

    /**
     * Marks an approved substitution done. False when it was not approved, or was already done —
     * which is the check that stops a duty being credited twice.
     */
    public boolean complete(long id, String completedBy, LocalDateTime now, String note) {
        return jdbc.update("UPDATE substitution_request SET status = ?, completed_by = ?, "
                        + "completed_at = ?, outcome = ? WHERE id = ? AND status = ?",
                SubstitutionStatus.COMPLETED.name(), completedBy, Timestamp.valueOf(now), note,
                id, SubstitutionStatus.APPROVED.name()) == 1;
    }

    /** The owner takes an undecided offer back. */
    public boolean withdraw(long id, String note) {
        return jdbc.update("UPDATE substitution_request SET status = ?, outcome = ? "
                        + "WHERE id = ? AND status IN (?, ?)",
                SubstitutionStatus.WITHDRAWN.name(), note, id,
                SubstitutionStatus.OFFERED.name(), SubstitutionStatus.CLAIMED.name()) == 1;
    }

    /**
     * Closes off offers whose sitting has been and gone without a decision.
     *
     * <p>Approved ones are left alone: the duty did move, and whether the substitute turned up
     * is a question for a person, not for a clock.
     */
    public int expirePast(LocalDateTime now) {
        return jdbc.update("UPDATE substitution_request SET status = ?, outcome = ? "
                        + "WHERE ends_at < ? AND status IN (?, ?)",
                SubstitutionStatus.EXPIRED.name(), "The sitting passed without a decision",
                Timestamp.valueOf(now), SubstitutionStatus.OFFERED.name(), SubstitutionStatus.CLAIMED.name());
    }

    public List<SubstitutionRequest> involving(String facultyId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM substitution_request "
                + "WHERE original_faculty_id = ? OR volunteer_faculty_id = ? "
                + "ORDER BY starts_at DESC, id DESC", MAPPER, facultyId, facultyId);
    }
}
