package edsa.data;

import edsa.core.TimingOverride;
import edsa.core.TimingOverrides;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The timing changes in force, and the append-only history of every change ever made.
 *
 * <p>Two tables and not one, because they answer different questions. The override table holds
 * what is true now and is overwritten each time a paper is re-timed; the change table holds what
 * happened and is only ever added to. Keeping the history in the same row as the current value
 * would mean a correction erased the thing it was correcting.
 */
@Repository
public class TimingRepository {

    private static final RowMapper<TimingOverride> OVERRIDE = (rs, row) -> new TimingOverride(
            rs.getString("exam_id"),
            rs.getDate("exam_date") == null ? null : rs.getDate("exam_date").toLocalDate(),
            rs.getTime("start_time") == null ? null : rs.getTime("start_time").toLocalTime(),
            rs.getObject("duration_minutes") == null ? null : rs.getInt("duration_minutes"));

    private static final RowMapper<TimingChange> CHANGE = (rs, row) -> new TimingChange(
            rs.getLong("id"),
            rs.getString("exam_id"),
            rs.getString("paper_code"),
            rs.getString("was"),
            rs.getString("became"),
            rs.getString("scope"),
            rs.getString("changed_by"),
            rs.getTimestamp("changed_at").toLocalDateTime(),
            rs.getString("warnings"));

    private final JdbcTemplate jdbc;

    public TimingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TimingOverrides inForce() {
        return TimingOverrides.of(jdbc.query(
                "SELECT exam_id, exam_date, start_time, duration_minutes FROM exam_time_override "
                        + "ORDER BY exam_id", OVERRIDE));
    }

    /** Stores the change in force, replacing any earlier one for the same paper. */
    public void save(TimingOverride override, String changedBy) {
        Date date = override.date() == null ? null : Date.valueOf(override.date());
        Time start = override.start() == null ? null : Time.valueOf(override.start());
        int updated = jdbc.update("UPDATE exam_time_override SET exam_date = ?, start_time = ?, "
                        + "duration_minutes = ?, changed_by = ?, changed_at = ? WHERE exam_id = ?",
                date, start, override.minutes(), changedBy, Timestamp.valueOf(LocalDateTime.now()),
                override.examId());
        if (updated == 0) {
            jdbc.update("INSERT INTO exam_time_override (exam_id, exam_date, start_time, duration_minutes, "
                            + "changed_by, changed_at) VALUES (?, ?, ?, ?, ?, ?)",
                    override.examId(), date, start, override.minutes(), changedBy,
                    Timestamp.valueOf(LocalDateTime.now()));
        }
    }

    /** Puts a paper back on its declared timing. The history of how it got here stays. */
    public void clear(String examId) {
        jdbc.update("DELETE FROM exam_time_override WHERE exam_id = ?", examId);
    }

    /**
     * Records what was changed. Never updates: this is the audit history, and a history that can
     * be rewritten answers nothing.
     */
    public void record(TimingChange change) {
        jdbc.update("INSERT INTO exam_time_change (exam_id, paper_code, was, became, scope, changed_by, "
                        + "changed_at, warnings) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                change.examId(), change.paperCode(), change.was(), change.became(), change.scope(),
                change.changedBy(), Timestamp.valueOf(change.changedAt()), change.warnings());
    }

    /** The whole history, most recent first, for the audit panel. */
    public List<TimingChange> history(int limit) {
        return jdbc.query("SELECT id, exam_id, paper_code, was, became, scope, changed_by, changed_at, "
                + "warnings FROM exam_time_change ORDER BY changed_at DESC, id DESC LIMIT ?", CHANGE, limit);
    }

    public List<TimingChange> historyFor(String examId) {
        return jdbc.query("SELECT id, exam_id, paper_code, was, became, scope, changed_by, changed_at, "
                + "warnings FROM exam_time_change WHERE exam_id = ? ORDER BY changed_at DESC, id DESC",
                CHANGE, examId);
    }
}
