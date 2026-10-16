package edsa.data;

import edsa.core.AbsenceWindow;
import edsa.core.Absence;
import edsa.core.LeaveRequest;
import edsa.core.LeaveStatus;

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
 * Recorded absences, stored in the application database through plain JDBC.
 *
 * <p>Requests are persisted rather than held in memory so a pending one survives a restart:
 * an absence nobody has reviewed yet is still waiting for review tomorrow.
 */
@Repository
public class LeaveRequestRepository {

    private static final String COLUMNS = "id, faculty_id, faculty_name, absent_from, absent_to, reason, "
            + "status, requested_at, confirm_by, decided_at, outcome";

    private static final RowMapper<LeaveRequest> MAPPER = (rs, row) -> new LeaveRequest(
            rs.getLong("id"),
            rs.getString("faculty_id"),
            rs.getString("faculty_name"),
            new AbsenceWindow(rs.getTimestamp("absent_from").toLocalDateTime(),
                    rs.getTimestamp("absent_to").toLocalDateTime()),
            rs.getString("reason"),
            LeaveStatus.of(rs.getString("status")),
            rs.getTimestamp("requested_at").toLocalDateTime(),
            rs.getTimestamp("confirm_by").toLocalDateTime(),
            rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toLocalDateTime(),
            rs.getString("outcome"));

    private final JdbcTemplate jdbc;

    public LeaveRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Stores a new request and returns it with the id the database gave it. */
    public LeaveRequest record(LeaveRequest request) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO leave_request (faculty_id, faculty_name, absent_from, absent_to, reason, "
                            + "status, requested_at, confirm_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, request.facultyId());
            statement.setString(2, request.facultyName());
            statement.setTimestamp(3, Timestamp.valueOf(request.window().from()));
            statement.setTimestamp(4, Timestamp.valueOf(request.window().to()));
            statement.setString(5, request.reason());
            statement.setString(6, request.status().name());
            statement.setTimestamp(7, Timestamp.valueOf(request.requestedAt()));
            statement.setTimestamp(8, Timestamp.valueOf(request.confirmBy()));
            return statement;
        }, keys);

        Number id = keys.getKey();
        return find(id == null ? 0 : id.longValue())
                .orElseThrow(() -> new IllegalStateException("the leave request was not stored"));
    }

    public Optional<LeaveRequest> find(long id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM leave_request WHERE id = ?", MAPPER, id));
        } catch (EmptyResultDataAccessException unknown) {
            return Optional.empty();
        }
    }

    /** Every request, newest first, so the Disruption page reads as a log. */
    public List<LeaveRequest> all() {
        return jdbc.query("SELECT " + COLUMNS + " FROM leave_request ORDER BY requested_at DESC, id DESC", MAPPER);
    }

    public List<LeaveRequest> withStatus(LeaveStatus status) {
        return jdbc.query("SELECT " + COLUMNS + " FROM leave_request WHERE status = ? "
                + "ORDER BY requested_at DESC, id DESC", MAPPER, status.name());
    }

    /**
     * Everyone whose confirmed absence keeps them off duty, so the repairer never hands a
     * room to a colleague who is away too.
     */
    public List<Absence> confirmedAbsences() {
        return jdbc.query("SELECT " + COLUMNS + " FROM leave_request WHERE status IN ('CONFIRMED', 'COMPLETED')",
                        MAPPER).stream()
                .map(LeaveRequest::asAbsence)
                .toList();
    }

    /**
     * Moves a request to a new status, but only from the status it is expected to be in.
     *
     * <p>The {@code WHERE status = ?} is what stops two administrators deciding the same
     * request twice, or a reassignment running against a request somebody has since rejected:
     * the second update matches no row and the caller is told so.
     *
     * @return true when the row moved
     */
    public boolean moveStatus(long id, LeaveStatus from, LeaveStatus to, LocalDateTime when,
                              String decidedBy, String outcome) {
        return jdbc.update("UPDATE leave_request SET status = ?, decided_at = ?, decided_by = ?, outcome = ? "
                        + "WHERE id = ? AND status = ?",
                to.name(), Timestamp.valueOf(when), decidedBy, outcome, id, from.name()) == 1;
    }

    /** Replaces the outcome text without changing the status, after a reassignment ran. */
    public void recordOutcome(long id, String outcome) {
        jdbc.update("UPDATE leave_request SET outcome = ? WHERE id = ?", outcome, id);
    }
}
