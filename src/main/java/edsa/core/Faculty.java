package edsa.core;

import java.util.Set;

/**
 * A faculty member available to invigilate.
 *
 * <p>The subject codes are the papers they teach. Invigilator-only staff who teach
 * nothing carry an empty set.
 *
 * <p>Seniority is recorded two ways on purpose. The designation is the rank as the workbook
 * writes it, which {@link SeniorityLadder} turns into a position. The seniority value is an
 * explicit position that overrides it, because a title is a reasonable default and not an
 * authority: length of service or an administrative role can place somebody differently from
 * their rank. Both are optional, and a record with neither behaves exactly as it did before
 * either existed.
 */
public final class Faculty extends Person {

    private final boolean senior;
    private final Set<String> subjectCodes;
    private final String designation;
    private final Integer seniorityValue;

    public Faculty(String id, String name, String department, boolean senior, Set<String> subjectCodes) {
        this(id, name, department, senior, subjectCodes, null, null);
    }

    public Faculty(String id, String name, String department, boolean senior, Set<String> subjectCodes,
                   String designation, Integer seniorityValue) {
        super(id, name, department);
        this.senior = senior;
        this.subjectCodes = Set.copyOf(subjectCodes);
        this.designation = designation == null || designation.isBlank() ? null : designation.trim();
        this.seniorityValue = seniorityValue;
    }

    public boolean isSenior() {
        return senior;
    }

    /** The rank as the workbook wrote it, or null where none was given. */
    public String getDesignation() {
        return designation;
    }

    /** An explicit ladder position that overrides the designation, or null where none was given. */
    public Integer getSeniorityValue() {
        return seniorityValue;
    }

    /** The same member with a designation and seniority value attached. */
    public Faculty withSeniority(String designation, Integer seniorityValue) {
        return new Faculty(getId(), getName(), getDepartment(), senior, subjectCodes, designation, seniorityValue);
    }

    public Set<String> getSubjectCodes() {
        return subjectCodes;
    }

    public boolean teaches(String paperCode) {
        return subjectCodes.contains(paperCode);
    }
}
