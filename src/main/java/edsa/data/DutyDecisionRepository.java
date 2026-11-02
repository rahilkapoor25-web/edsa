package edsa.data;

import edsa.core.DutyDecision;
import edsa.core.DutyOrigin;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The duties a coordinator has stood behind, stored so they survive a recompute.
 *
 * <p>One decision per room-sitting, which the table's unique key enforces, so saving a decision
 * is an upsert rather than an insert: deciding twice about the same room replaces the earlier
 * decision instead of leaving two to argue with each other.
 */
@Repository
public class DutyDecisionRepository {

    private static final String COLUMNS = "id, slot_id, room_id, faculty_id, origin, decided_by, decided_at, note";

    private static final RowMapper<DutyDecision> MAPPER = (rs, row) -> new DutyDecision(
            rs.getLong("id"),
            rs.getString("slot_id"),
            rs.getString("room_id"),
            rs.getString("faculty_id"),
            DutyOrigin.valueOf(rs.getString("origin")),
            rs.getString("decided_by"),
            rs.getTimestamp("decided_at").toLocalDateTime(),
            rs.getString("note"));

    private final JdbcTemplate jdbc;

    public DutyDecisionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DutyDecision> all() {
        return jdbc.query("SELECT " + COLUMNS + " FROM duty_decision ORDER BY slot_id, room_id", MAPPER);
    }

    /** Stores a decision, replacing any earlier one for the same room-sitting. */
    public void save(DutyDecision decision) {
        int updated = jdbc.update("UPDATE duty_decision SET faculty_id = ?, origin = ?, decided_by = ?, "
                        + "decided_at = ?, note = ? WHERE slot_id = ? AND room_id = ?",
                decision.facultyId(), decision.origin().name(), decision.decidedBy(),
                Timestamp.valueOf(decision.decidedAt()), decision.note(),
                decision.slotId(), decision.roomId());
        if (updated == 0) {
            jdbc.update("INSERT INTO duty_decision (slot_id, room_id, faculty_id, origin, decided_by, "
                            + "decided_at, note) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    decision.slotId(), decision.roomId(), decision.facultyId(), decision.origin().name(),
                    decision.decidedBy(), Timestamp.valueOf(decision.decidedAt()), decision.note());
        }
    }

    /** Takes a decision back, so the room returns to whatever the allocator recommends. */
    public void clear(String slotId, String roomId) {
        jdbc.update("DELETE FROM duty_decision WHERE slot_id = ? AND room_id = ?", slotId, roomId);
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM duty_decision", Integer.class);
        return count == null ? 0 : count;
    }
}
