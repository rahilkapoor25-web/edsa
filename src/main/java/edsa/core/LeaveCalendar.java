package edsa.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is away, and when.
 *
 * <p>Only confirmed absence belongs in here. A recorded absence nobody has reviewed changes no
 * duty, which is a decision taken where absences are recorded and not one this class should
 * second-guess; by the time a window reaches the calendar somebody has approved it.
 *
 * <p>Windows are intervals, start-inclusive and end-exclusive like every other interval in the
 * application, so somebody away 09:00–12:00 cannot take the paper running 09:00–11:00 and can
 * take the one starting at 12:00.
 */
public final class LeaveCalendar {

    private final Map<String, List<AbsenceWindow>> windows = new LinkedHashMap<>();

    public static LeaveCalendar empty() {
        return new LeaveCalendar();
    }

    public LeaveCalendar add(String facultyId, AbsenceWindow window) {
        windows.computeIfAbsent(facultyId, key -> new ArrayList<>()).add(window);
        return this;
    }

    /** Whether this member is away for any part of the given sitting. */
    public boolean isAway(String facultyId, ExamSlot slot) {
        return windows.getOrDefault(facultyId, List.of()).stream()
                .anyMatch(window -> window.covers(slot));
    }

    public List<AbsenceWindow> windowsFor(String facultyId) {
        return List.copyOf(windows.getOrDefault(facultyId, List.of()));
    }

    public boolean isEmpty() {
        return windows.isEmpty();
    }
}
