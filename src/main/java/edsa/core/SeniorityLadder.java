package edsa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ranks an examination office recognises, in the order it considers them senior.
 *
 * <p>Two things this deliberately does not do. It does not treat a designation as the last word
 * on seniority: a faculty record may carry an explicit seniority value, and where it does that
 * value wins, because length of service, an administrative role or a local agreement can place
 * somebody differently from their title. And it does not ship anybody's real policy — the
 * default ladder below lists the ranks that appear in Thapar's published recruitment and
 * promotion policy, with weights this application invented and an examination office is expected
 * to replace. No weight here is an institutional rule.
 *
 * <p>An unrecognised designation is not an error. Imported data is never as tidy as a
 * configuration file, so an unknown rank is simply placed at {@link #unknownRank()} and reported
 * by the pages, rather than stopping a plan from being built.
 */
public final class SeniorityLadder {

    /** Where a designation nobody configured sits: below every configured rank. */
    private static final int UNKNOWN_RANK = 0;

    private final Map<String, Designation> byCode = new LinkedHashMap<>();
    private final int seniorFrom;

    /**
     * @param designations the configured ranks, in any order
     * @param seniorFrom   the seniority rank at which somebody counts as senior for the purpose
     *                     of the rule that every room should have a senior invigilator
     */
    public SeniorityLadder(List<Designation> designations, int seniorFrom) {
        for (Designation designation : designations) {
            byCode.put(normalise(designation.code()), designation);
        }
        this.seniorFrom = seniorFrom;
    }

    /**
     * The ranks named in Thapar's published Recruitment and Promotion Policy 2020, with weights
     * this application chose.
     *
     * <p>The policy names Professor, Associate Professor, Assistant Professor at three levels and
     * contractual Lecturer. It does not name a "Senior Professor", so neither does this, and the
     * duty shares are not from it: they encode the common practice of asking less invigilation of
     * more senior staff, which an office should confirm and adjust rather than inherit.
     */
    public static SeniorityLadder defaults() {
        return new SeniorityLadder(List.of(
                new Designation("PROFESSOR", "Professor", 50, 0.5),
                new Designation("ASSOCIATE_PROFESSOR", "Associate Professor", 40, 0.75),
                new Designation("ASSISTANT_PROFESSOR_III", "Assistant Professor (III)", 33, 1.0),
                new Designation("ASSISTANT_PROFESSOR_II", "Assistant Professor (II)", 32, 1.0),
                new Designation("ASSISTANT_PROFESSOR_I", "Assistant Professor (I)", 31, 1.0),
                new Designation("LECTURER", "Lecturer", 20, 1.0)),
                40);
    }

    public List<Designation> designations() {
        return byCode.values().stream()
                .sorted(Comparator.comparingInt(Designation::seniorityRank).reversed()
                        .thenComparing(Designation::code))
                .toList();
    }

    public Optional<Designation> find(String code) {
        return Optional.ofNullable(byCode.get(normalise(code)));
    }

    public boolean recognises(String code) {
        return code != null && !code.isBlank() && byCode.containsKey(normalise(code));
    }

    public int unknownRank() {
        return UNKNOWN_RANK;
    }

    public int seniorFrom() {
        return seniorFrom;
    }

    /**
     * Where this member sits on the ladder.
     *
     * <p>An explicit seniority value on the record wins over the designation, which is the whole
     * point of supporting one: the title is a good default and not an authority.
     */
    public int seniorityOf(Faculty member) {
        if (member.getSeniorityValue() != null) {
            return member.getSeniorityValue();
        }
        return find(member.getDesignation()).map(Designation::seniorityRank).orElse(UNKNOWN_RANK);
    }

    /** The share of duties expected of this member, from their rank or the full share. */
    public double dutyShareOf(Faculty member) {
        return find(member.getDesignation()).map(Designation::dutyShare).orElse(1.0);
    }

    /**
     * Whether this member counts as senior for the senior-cover preference.
     *
     * <p>The workbook's own senior flag still counts: a record marked senior is senior whatever
     * its designation says, so data that predates the ladder keeps working.
     */
    public boolean isSenior(Faculty member) {
        return member.isSenior() || seniorityOf(member) >= seniorFrom;
    }

    /** The designations present in the data that the ladder does not recognise, for reporting. */
    public List<String> unrecognisedIn(List<Faculty> faculty) {
        List<String> unknown = new ArrayList<>();
        for (Faculty member : faculty) {
            String code = member.getDesignation();
            if (code != null && !code.isBlank() && !recognises(code) && !unknown.contains(code)) {
                unknown.add(code);
            }
        }
        return List.copyOf(unknown);
    }

    private static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    }
}
