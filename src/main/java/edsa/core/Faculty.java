package edsa.core;

import java.util.Set;

/**
 * A faculty member available to invigilate.
 *
 * <p>The subject codes are the papers they teach. Invigilator-only staff who teach
 * nothing carry an empty set.
 */
public final class Faculty extends Person {

    private final boolean senior;
    private final Set<String> subjectCodes;

    public Faculty(String id, String name, String department, boolean senior, Set<String> subjectCodes) {
        super(id, name, department);
        this.senior = senior;
        this.subjectCodes = Set.copyOf(subjectCodes);
    }

    public boolean isSenior() {
        return senior;
    }

    public Set<String> getSubjectCodes() {
        return subjectCodes;
    }

    public boolean teaches(String paperCode) {
        return subjectCodes.contains(paperCode);
    }
}
