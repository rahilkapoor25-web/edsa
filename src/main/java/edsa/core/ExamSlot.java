package edsa.core;

import java.time.LocalDate;
import java.time.LocalTime;

/** One paper sat at one date and time. */
public final class ExamSlot {

    private final String id;
    private final String paperCode;
    private final String paperName;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public ExamSlot(String id, String paperCode, String paperName,
                    LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.paperCode = paperCode;
        this.paperName = paperName;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
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

    public LocalTime getEndTime() {
        return endTime;
    }

    @Override
    public String toString() {
        return paperCode + " on " + date + " at " + startTime;
    }
}
