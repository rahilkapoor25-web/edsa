package edsa.data;

import edsa.core.PointsBalance;
import edsa.core.PointsEntry;
import edsa.core.PointsKind;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The points ledger: append-only, and the only place a balance comes from.
 *
 * <p>There is deliberately no balance column anywhere. A stored balance and a stored history can
 * disagree, and when they do there is no way to tell which is right; summing the rows every time
 * costs one indexed query and cannot drift. The same reasoning is why nothing here updates or
 * deletes a row — a correction is another row, so the history always explains the balance it
 * produces.
 */
@Repository
public class PointsRepository {

    private static final String COLUMNS = "id, faculty_id, kind, points, substitution_id, redemption_id, "
            + "created_at, created_by, expires_at, note";

    private static final RowMapper<PointsEntry> MAPPER = (rs, row) -> new PointsEntry(
            rs.getLong("id"),
            rs.getString("faculty_id"),
            PointsKind.of(rs.getString("kind")),
            rs.getInt("points"),
            rs.getObject("substitution_id") == null ? null : rs.getLong("substitution_id"),
            rs.getObject("redemption_id") == null ? null : rs.getLong("redemption_id"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getString("created_by"),
            rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toLocalDateTime(),
            rs.getString("note"));

    private final JdbcTemplate jdbc;

    public PointsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Credits a completed substitution.
     *
     * <p>Returns false rather than throwing when this substitution has already been credited.
     * The table's unique key is what actually prevents the second award — two administrators
     * completing the same substitution at the same moment would both find no existing award —
     * so the duplicate arrives here as a constraint violation, and a duplicate award is a
     * no-op rather than an error worth stopping a transaction for.
     */
    public boolean award(String facultyId, int points, long substitutionId, String createdBy,
                         LocalDateTime now, LocalDateTime expiresAt, String note) {
        try {
            jdbc.update("INSERT INTO points_transaction (faculty_id, kind, points, substitution_id, "
                            + "created_at, created_by, expires_at, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    facultyId, PointsKind.EARNED.name(), points, substitutionId,
                    Timestamp.valueOf(now), createdBy,
                    expiresAt == null ? null : Timestamp.valueOf(expiresAt), note);
            return true;
        } catch (DuplicateKeyException alreadyAwarded) {
            return false;
        }
    }

    /** Spends points on an approved exemption. Stored as a negative row, never as a subtraction. */
    public void redeem(String facultyId, int points, long redemptionId, String createdBy,
                       LocalDateTime now, String note) {
        jdbc.update("INSERT INTO points_transaction (faculty_id, kind, points, redemption_id, "
                        + "created_at, created_by, note) VALUES (?, ?, ?, ?, ?, ?, ?)",
                facultyId, PointsKind.REDEEMED.name(), -Math.abs(points), redemptionId,
                Timestamp.valueOf(now), createdBy, note);
    }

    /** An administrator's correction, in either direction, with a reason attached. */
    public void adjust(String facultyId, int points, String createdBy, LocalDateTime now, String note) {
        jdbc.update("INSERT INTO points_transaction (faculty_id, kind, points, created_at, created_by, note) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                facultyId, PointsKind.ADJUSTED.name(), points, Timestamp.valueOf(now), createdBy, note);
    }

    public List<PointsEntry> entriesFor(String facultyId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM points_transaction WHERE faculty_id = ? "
                + "ORDER BY created_at DESC, id DESC", MAPPER, facultyId);
    }

    public List<PointsEntry> all() {
        return jdbc.query("SELECT " + COLUMNS + " FROM points_transaction "
                + "ORDER BY created_at DESC, id DESC", MAPPER);
    }

    public Optional<PointsEntry> awardFor(long substitutionId) {
        List<PointsEntry> found = jdbc.query("SELECT " + COLUMNS + " FROM points_transaction "
                + "WHERE substitution_id = ? AND kind = ?", MAPPER, substitutionId, PointsKind.EARNED.name());
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * Writes off credits that were never used in time.
     *
     * <p>Each lapsed credit is written off individually rather than as one lump, so the history
     * still shows which award expired. Already-expired credits are skipped by looking for the
     * matching write-off, which is the only way to make running this twice harmless.
     */
    public int expireLapsed(LocalDateTime now, String runBy) {
        List<PointsEntry> lapsed = jdbc.query("SELECT " + COLUMNS + " FROM points_transaction e "
                        + "WHERE e.kind = ? AND e.expires_at IS NOT NULL AND e.expires_at <= ? "
                        + "AND NOT EXISTS (SELECT 1 FROM points_transaction w WHERE w.kind = ? "
                        + "AND w.substitution_id = e.substitution_id AND w.faculty_id = e.faculty_id)",
                MAPPER, PointsKind.EARNED.name(), Timestamp.valueOf(now), PointsKind.EXPIRED.name());

        for (PointsEntry entry : lapsed) {
            jdbc.update("INSERT INTO points_transaction (faculty_id, kind, points, substitution_id, "
                            + "created_at, created_by, note) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    entry.facultyId(), PointsKind.EXPIRED.name(), -entry.points(), entry.substitutionId(),
                    Timestamp.valueOf(now), runBy,
                    "Earned " + entry.createdAt().toLocalDate() + ", unused by " + entry.expiresAt().toLocalDate());
        }
        return lapsed.size();
    }

    /** The balance, with points held by undecided requests passed in by the caller. */
    public PointsBalance balanceOf(String facultyId, int pendingHeld) {
        return PointsBalance.from(facultyId, entriesFor(facultyId), pendingHeld);
    }
}
