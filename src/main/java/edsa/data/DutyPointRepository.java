package edsa.data;

import edsa.core.DutyPoint;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The duty points ledger. Rows are only ever added — one per duty missed or covered — so every
 * total can be explained from the history beneath it.
 */
@Repository
public class DutyPointRepository {

    /** One line of the history. */
    public record Entry(long id, String facultyId, String facultyName, int points, String reason,
                        Long leaveRequestId, LocalDateTime recordedAt) {

        /** "+1" or "−1", with a real minus sign. */
        public String signed() {
            return points > 0 ? "+" + points : "−" + (-points);
        }
    }

    /** One invigilator's running totals: duties covered for others, and duties missed. */
    public record Tally(String facultyName, int covered, int missed) {

        public int points() {
            return covered - missed;
        }
    }

    private final JdbcTemplate jdbc;

    public DutyPointRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(long leaveRequestId, List<DutyPoint> points) {
        jdbc.batchUpdate("INSERT INTO duty_point (faculty_id, faculty_name, points, reason, leave_request_id) "
                        + "VALUES (?, ?, ?, ?, ?)",
                points, points.size(), (statement, point) -> {
                    statement.setString(1, point.facultyId());
                    statement.setString(2, point.facultyName());
                    statement.setInt(3, point.points());
                    statement.setString(4, point.reason());
                    statement.setLong(5, leaveRequestId);
                });
    }

    /** Totals per faculty id, for everybody who has ever gained or lost a point. */
    public Map<String, Tally> tallies() {
        Map<String, Tally> tallies = new LinkedHashMap<>();
        jdbc.query("SELECT faculty_id, MAX(faculty_name) AS faculty_name, "
                        + "SUM(CASE WHEN points > 0 THEN points ELSE 0 END) AS covered, "
                        + "SUM(CASE WHEN points < 0 THEN -points ELSE 0 END) AS missed "
                        + "FROM duty_point GROUP BY faculty_id ORDER BY faculty_id",
                (java.sql.ResultSet rs) -> {
                    tallies.put(rs.getString("faculty_id"),
                            new Tally(rs.getString("faculty_name"), rs.getInt("covered"), rs.getInt("missed")));
                });
        return tallies;
    }

    /** Every entry, newest first. */
    public List<Entry> history() {
        return jdbc.query("SELECT id, faculty_id, faculty_name, points, reason, leave_request_id, recorded_at "
                        + "FROM duty_point ORDER BY recorded_at DESC, id DESC",
                (rs, row) -> new Entry(rs.getLong("id"), rs.getString("faculty_id"), rs.getString("faculty_name"),
                        rs.getInt("points"), rs.getString("reason"), rs.getObject("leave_request_id", Long.class),
                        rs.getTimestamp("recorded_at").toLocalDateTime()));
    }
}
