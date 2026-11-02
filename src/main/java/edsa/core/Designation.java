package edsa.core;

/**
 * One rank on the faculty ladder, with the weight it carries when duties are shared out.
 *
 * <p>A designation is configuration, not a fact about the world. Institutions name their ranks
 * differently and change them, and the order is not always the obvious one, so nothing here
 * hard-codes a list: {@link SeniorityLadder} holds whichever ranks an examination office has
 * configured, and this record is one entry in it.
 *
 * @param code          how the rank is written in the imported workbook, matched case-insensitively
 * @param label         how it is shown on a page
 * @param seniorityRank the position on the ladder; a larger number is more senior. Gaps are
 *                      deliberate, so a rank can be inserted between two others without
 *                      renumbering the ones around it.
 * @param dutyShare     the share of an equal workload this rank is expected to carry. 1.0 is a
 *                      full share; 0.5 asks half as many duties as a full-share colleague, and
 *                      0.0 excuses the rank from the automatic allocation altogether. It is a
 *                      configured expectation, never an entitlement this code claims to know.
 */
public record Designation(String code, String label, int seniorityRank, double dutyShare) {

    public Designation {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("a designation needs a code");
        }
        if (dutyShare < 0) {
            throw new IllegalArgumentException("a duty share cannot be negative: " + dutyShare);
        }
        code = code.trim();
        label = label == null || label.isBlank() ? code : label.trim();
    }

    /** Whether this rank takes part in the automatic allocation at all. */
    public boolean carriesDuties() {
        return dutyShare > 0;
    }

    /** The key duties are spread by: a full-share rank counts one duty as one. */
    public double loadPerDuty() {
        return carriesDuties() ? 1.0 / dutyShare : Double.MAX_VALUE;
    }
}
