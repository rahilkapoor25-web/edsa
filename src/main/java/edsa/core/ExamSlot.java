package edsa.core;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One paper sat at one date and time, for a fixed number of minutes.
 *
 * <p>The interval is start-inclusive and end-exclusive, so a sitting that ends at 11:00 may be
 * followed by another that starts at 11:00.
 */
public final class ExamSlot {

    private final String id;
    private final String paperCode;
    private final String paperName;
    private final LocalDate date;
    private final LocalTime startTime;
    private final int durationMinutes;

    public ExamSlot(String id, String paperCode, String paperName,
                    LocalDate date, LocalTime startTime, int durationMinutes) {
        this.id = id;
        this.paperCode = paperCode;
        this.paperName = paperName;
        this.date = date;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
    }

    /** Builds a slot from an explicit finish time rather than a duration. */
    public static ExamSlot between(String id, String paperCode, String paperName,
                                   LocalDate date, LocalTime startTime, LocalTime endTime) {
        return new ExamSlot(id, paperCode, paperName, date, startTime,
                (int) Duration.between(startTime, endTime).toMinutes());
    }

    public String getId() {
        return id;
    }

    public String getPaperCode() {
        return paperCode;
    }

    public String getPaperName() {
        return paperName;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public LocalTime getEndTime() {
        return startTime.plusMinutes(durationMinutes);
    }

    /**
     * True when the two sittings share any moment of the same day, so nobody and no room can
     * be in both. Touching intervals do not overlap.
     */
    public boolean overlaps(ExamSlot other) {
        return date.equals(other.date)
                && startTime.isBefore(other.getEndTime())
                && other.startTime.isBefore(getEndTime());
    }

    @Override
    public String toString() {
        return paperCode + " on " + date + " at " + startTime + "-" + getEndTime();
    }
}
