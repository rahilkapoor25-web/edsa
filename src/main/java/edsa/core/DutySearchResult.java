package edsa.core;

import java.util.List;

/**
 * One invigilator who matched a search, with every duty they hold.
 *
 * <p>Results are grouped per person rather than returned as a flat list of duties, so two
 * colleagues with similar names come back as two groups — each with its own id and department
 * — instead of one confusing run of rows.
 *
 * <p>A match with no duties is still a match: somebody searching for their own name needs to
 * be told they have nothing this session, which is different from not being found at all.
 */
public record DutySearchResult(Faculty member, List<FacultyDuty> duties) {

    public int dutyCount() {
        return duties.size();
    }

    public boolean hasDuties() {
        return !duties.isEmpty();
    }

    /** The distinct rooms they are in, for a one-line summary. */
    public String roomSummary() {
        return duties.stream()
                .map(duty -> duty.room().getId())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }
}
