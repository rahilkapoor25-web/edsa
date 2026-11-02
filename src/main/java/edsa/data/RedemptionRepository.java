package edsa.data;

import edsa.core.DutyRedemption;
import edsa.core.RedemptionStatus;

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
 * Requests to be excused a duty, and what was decided about them.
 *
 * <p>Like the substitution table, every decision is a conditional update on the pending status,
 * so a request cannot be approved twice — which matters here because an approval spends points.
 */
@Repository
public class RedemptionRepository {

    private static final String COLUMNS = "id, faculty_id, slot_id, room_id, paper_code, starts_at, "
            + "points_spent, status, reason, requested_at, decided_by, decided_at, outcome";

    private static final RowMapper<DutyRedemption> MAPPER = (rs, row) -> new DutyRedemption(
            rs.getLong("id"),
            rs.getString("faculty_id"),
            rs.getString("slot_id"),
            rs.getString("room_id"),
            rs.getString("paper_code"),
            rs.getTimestamp("starts_at").toLocalDateTime(),
            rs.getInt("points_spent"),
            RedemptionStatus.of(rs.getString("status")),
            rs.getString("reason"),
            rs.getTimestamp("requested_at").toLocalDateTime(),
            rs.getString("decided_by"),
            rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toLocalDateTime(),
            rs.getString("outcome"));

    private final JdbcTemplate jdbc;

    public RedemptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DutyRedemption request(DutyRedemption redemption) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO duty_redemption (faculty_id, slot_id, room_id, paper_code, starts_at, "
                            + "points_spent, status, reason, requested_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, redemption.facultyId());
            statement.setString(2, redemption.slotId());
            statement.setString(3, redemption.roomId());
            statement.setString(4, redemption.paperCode());
            statement.setTimestamp(5, Timestamp.valueOf(redemption.startsAt()));
            statement.setInt(6, redemption.pointsSpent());
            statement.setString(7, redemption.status().name());
            statement.setString(8, redemption.reason());
            statement.setTimestamp(9, Timestamp.valueOf(redemption.requestedAt()));
            return statement;
        }, keys);
        Number id = keys.getKey();
        return find(id == null ? 0 : id.longValue())
                .orElseThrow(() -> new IllegalStateException("the redemption request was not stored"));
    }

    public Optional<DutyRedemption> find(long id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM duty_redemption WHERE id = ?", MAPPER, id));
        } catch (EmptyResultDataAccessException unknown) {
            return Optional.empty();
        }
    }

    public List<DutyRedemption> all() {
        return jdbc.query("SELECT " + COLUMNS + " FROM duty_redemption ORDER BY requested_at DESC, id DESC",
                MAPPER);
    }

    public List<DutyRedemption> awaitingDecision() {
        return jdbc.query("SELECT " + COLUMNS + " FROM duty_redemption WHERE status = ? "
                + "ORDER BY starts_at, id", MAPPER, RedemptionStatus.PENDING.name());
    }

    public List<DutyRedemption> forFaculty(String facultyId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM duty_redemption WHERE faculty_id = ? "
                + "ORDER BY requested_at DESC, id DESC", MAPPER, facultyId);
    }

    /** Points this person has committed to requests nobody has decided, so they cannot be spent twice. */
    public int pendingPointsFor(String facultyId) {
        Integer held = jdbc.queryForObject("SELECT COALESCE(SUM(points_spent), 0) FROM duty_redemption "
                + "WHERE faculty_id = ? AND status = ?", Integer.class, facultyId, RedemptionStatus.PENDING.name());
        return held == null ? 0 : held;
    }

    /** How many duties this person has already been excused, against the per-session limit. */
    public int approvedCountFor(String facultyId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM duty_redemption WHERE faculty_id = ? "
                + "AND status = ?", Integer.class, facultyId, RedemptionStatus.APPROVED.name());
        return count == null ? 0 : count;
    }

    /** Decides a pending request. False when it was already decided. */
    public boolean decide(long id, RedemptionStatus outcome, String decidedBy, LocalDateTime now, String note) {
        return jdbc.update("UPDATE duty_redemption SET status = ?, decided_by = ?, decided_at = ?, "
                        + "outcome = ? WHERE id = ? AND status = ?",
                outcome.name(), decidedBy, Timestamp.valueOf(now), note, id,
                RedemptionStatus.PENDING.name()) == 1;
    }
}
