package edsa.core;

import java.util.Set;

/** A student, and the papers they are registered to sit. */
public final class Student extends Person {

    private final Set<String> paperCodes;

    public Student(String id, String name, String department, Set<String> paperCodes) {
        super(id, name, department);
        this.paperCodes = Set.copyOf(paperCodes);
    }

    public Set<String> getPaperCodes() {
        return paperCodes;
    }

    public boolean isRegisteredFor(String paperCode) {
        return paperCodes.contains(paperCode);
    }
}
