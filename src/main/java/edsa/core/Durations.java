package edsa.core;

/** How a length of time is written on screen and in exports: 10 min, 90 min, 2 h, 3 h 30 min. */
public final class Durations {

    private Durations() {
    }

    /**
     * A time of day written from a count of minutes since midnight, for saying when a sitting
     * would have finished even though it ran past the end of the day and so has no valid time.
     */
    public static String clock(long minuteOfDay) {
        long hours = minuteOfDay / 60;
        long minutes = minuteOfDay % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    public static String label(int minutes) {
        if (minutes <= 0) {
            return minutes + " min";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }
}
