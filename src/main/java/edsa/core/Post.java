package edsa.core;

/**
 * A teacher's post, which decides their seniority on the duty roster.
 *
 * <p>The posts are fixed: a Professor is senior; an Associate or Assistant Professor is junior.
 * A junior never invigilates a room alone — every room has a senior in it.
 */
public enum Post {

    PROFESSOR("Professor", true),
    ASSOCIATE_PROFESSOR("Associate Professor", false),
    ASSISTANT_PROFESSOR("Assistant Professor", false);

    private final String label;
    private final boolean senior;

    Post(String label, boolean senior) {
        this.label = label;
        this.senior = senior;
    }

    public String label() {
        return label;
    }

    public boolean isSenior() {
        return senior;
    }

    /** The post a workbook names, ignoring case and surrounding space; null when it names none of them. */
    public static Post of(String text) {
        if (text == null) {
            return null;
        }
        String wanted = text.trim().replaceAll("\\s+", " ");
        for (Post post : values()) {
            if (post.label.equalsIgnoreCase(wanted)) {
                return post;
            }
        }
        return null;
    }
}
