package edsa.core;

/** How a length of time is written on screen and in exports: 10 min, 90 min, 2 h, 3 h 30 min. */
public final class Durations {

    private Durations() {
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
