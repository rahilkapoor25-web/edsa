package edsa.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * The stretch of time somebody is away: a few hours, one day, or several.
 *
 * <p>Like every other interval in the system this is start-inclusive and end-exclusive, so an
 * absence that ends at 11:00 does not touch a sitting that starts at 11:00. That is what makes
 * a morning's leave take only the morning's duties: a professor away from 09:00 to 12:00 loses
 * the paper that runs 09:00–11:00 and keeps the one that runs 14:00–17:00.
 */
public record AbsenceWindow(LocalDateTime from, LocalDateTime to) {

    public AbsenceWindow {
        if (from == null || to == null) {
            throw new IllegalArgumentException("an absence needs a start and an end");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("an absence must end after it starts, got "
                    + from + " to " + to);
        }
    }

    /** From one moment to another, possibly spanning days. */
    public static AbsenceWindow between(LocalDateTime from, LocalDateTime to) {
        return new AbsenceWindow(from, to);
    }

    /** Part of one day, such as a morning. */
    public static AbsenceWindow hours(LocalDate day, LocalTime from, LocalTime to) {
        return new AbsenceWindow(LocalDateTime.of(day, from), LocalDateTime.of(day, to));
    }

    /** One whole day, midnight to midnight. */
    public static AbsenceWindow wholeDay(LocalDate day) {
        return new AbsenceWindow(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    /** Every day from the first to the last inclusive. */
    public static AbsenceWindow days(LocalDate first, LocalDate last) {
        return new AbsenceWindow(first.atStartOfDay(), last.plusDays(1).atStartOfDay());
    }

    /**
     * The open end of an unbounded absence.
     *
     * <p>Deliberately a real date rather than {@link LocalDateTime#MIN}: these values are
     * stored in a {@code TIMESTAMP} column and read back, and the extremes of
     * {@code LocalDateTime} do not survive that round trip. No examination session falls
     * outside this range.
     */
    private static final LocalDateTime DAWN = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime DUSK = LocalDateTime.of(9999, 12, 31, 23, 59);

    /** The whole examination session, however long it turns out to be. */
    public static AbsenceWindow wholeSession() {
        return new AbsenceWindow(DAWN, DUSK);
    }

    public boolean isWholeSession() {
        return !from.isAfter(DAWN) && !to.isBefore(DUSK);
    }

    /**
     * True when this absence overlaps the sitting at all, so the person cannot invigilate it.
     *
     * <p>The sitting's end is derived from its duration rather than its end time, so a sitting
     * that ran past midnight is still measured correctly.
     */
    public boolean covers(ExamSlot slot) {
        LocalDateTime start = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        LocalDateTime end = start.plusMinutes(slot.getDurationMinutes());
        return start.isBefore(to) && from.isBefore(end);
    }

    /** True when the two absences share any moment, so one person cannot be in both. */
    public boolean overlaps(AbsenceWindow other) {
        return from.isBefore(other.to) && other.from.isBefore(to);
    }

    /** How the window reads on screen: "the whole session", one date, or a range. */
    public String describe() {
        if (isWholeSession()) {
            return "the whole session";
        }
        if (from.toLocalTime().equals(LocalTime.MIDNIGHT) && to.equals(from.toLocalDate().plusDays(1).atStartOfDay())) {
            return from.toLocalDate().toString();
        }
        if (from.toLocalDate().equals(to.toLocalDate())) {
            return from.toLocalDate() + " " + from.toLocalTime() + "–" + to.toLocalTime();
        }
        return from.toLocalDate() + " " + from.toLocalTime() + " to " + to.toLocalDate() + " " + to.toLocalTime();
    }
}
