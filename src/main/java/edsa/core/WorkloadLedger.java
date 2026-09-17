package edsa.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** How many duties each invigilator has been given. */
public final class WorkloadLedger {

    private final Map<String, Integer> duties = new LinkedHashMap<>();

    public void record(String facultyId) {
        duties.merge(facultyId, 1, Integer::sum);
    }

    public int dutiesFor(String facultyId) {
        return duties.getOrDefault(facultyId, 0);
    }

    public Map<String, Integer> duties() {
        return Collections.unmodifiableMap(duties);
    }
}
