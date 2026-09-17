package edsa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Who invigilates what. */
public final class DutyRoster {

    private final List<Assignment> assignments = new ArrayList<>();

    public void assign(Assignment assignment) {
        assignments.add(assignment);
    }

    /** Takes a duty back off, so a solver can try a candidate and think better of it. */
    public void remove(Assignment assignment) {
        assignments.remove(assignment);
    }

    public List<Assignment> all() {
        return Collections.unmodifiableList(assignments);
    }

    public List<Assignment> forSlot(String slotId) {
        return assignments.stream().filter(a -> a.slotId().equals(slotId)).toList();
    }

    public List<Assignment> forFaculty(String facultyId) {
        return assignments.stream().filter(a -> a.facultyId().equals(facultyId)).toList();
    }
}
