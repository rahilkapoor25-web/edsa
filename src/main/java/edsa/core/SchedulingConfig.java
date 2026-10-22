package edsa.core;

import java.time.LocalTime;

/**
 * The limits the timetable must fit inside: when the hall opens and closes, how long a gap to
 * leave between sittings in the same room, and what capacity to assume for a room whose own
 * capacity is missing.
 */
public final class SchedulingConfig {

    private final LocalTime dayStart;
    private final LocalTime dayEnd;
    private final int turnaroundMinutes;
    private final int fallbackRoomCapacity;

    public SchedulingConfig(LocalTime dayStart, LocalTime dayEnd, int turnaroundMinutes, int fallbackRoomCapacity) {
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.turnaroundMinutes = turnaroundMinutes;
        this.fallbackRoomCapacity = fallbackRoomCapacity;
    }

    public static SchedulingConfig defaults() {
        return new SchedulingConfig(LocalTime.of(9, 0), LocalTime.of(18, 0), 30, 30);
    }

    public LocalTime dayStart() {
        return dayStart;
    }

    public LocalTime dayEnd() {
        return dayEnd;
    }

    public int turnaroundMinutes() {
        return turnaroundMinutes;
    }

    public int fallbackRoomCapacity() {
        return fallbackRoomCapacity;
    }
}
