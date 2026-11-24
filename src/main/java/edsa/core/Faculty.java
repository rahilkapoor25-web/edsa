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
    private final Post post;
    private final Set<String> subjectCodes;

    public Faculty(String id, String name, String department, boolean senior, Set<String> subjectCodes) {
        super(id, name, department);
        this.senior = senior;
        this.post = null;
        this.subjectCodes = Set.copyOf(subjectCodes);
    }

    /** A member whose seniority comes from their post, as the workbook gives it. */
    public Faculty(String id, String name, String department, Post post) {
        super(id, name, department);
        this.senior = post != null && post.isSenior();
        this.post = post;
        this.subjectCodes = Set.of();
    }

    public boolean isSenior() {
        return senior;
    }

    /** The post, or null for data that only says senior or junior, such as the legacy CSV. */
    public Post getPost() {
        return post;
    }

    /** "Professor", "Assistant Professor"… or, without a post, "Senior" / "Junior". */
    public String getPostLabel() {
        return post != null ? post.label() : senior ? "Senior" : "Junior";
    }

    /** Whether this member belongs to that department, so may not invigilate its papers. */
    public boolean belongsTo(String department) {
        return department != null && getDepartment() != null
                && getDepartment().trim().equalsIgnoreCase(department.trim());
    }

    public Set<String> getSubjectCodes() {
        return subjectCodes;
    }

    public boolean teaches(String paperCode) {
        return subjectCodes.contains(paperCode);
    }
}
