package edsa.core;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Whether a particular member may take a particular duty, and why not when they may not.
 *
 * <p>The reason matters as much as the answer. A coordinator looking at an unstaffed room needs
 * to know whether every colleague was busy, on leave, at their cap or excused by rank, because
 * each of those asks something different of them. So this returns the refusal in words rather
 * than a boolean, and the allocator passes it straight through to the problem list.
 *
 * <p>The checks are ordered from the most absolute to the most negotiable, so the reason given
 * is the one a coordinator can least argue with.
 */
public final class DutyEligibility {

    private final SeniorityLadder ladder;
    private final DutyPolicy policy;
    private final LeaveCalendar leave;

    public DutyEligibility(SeniorityLadder ladder, DutyPolicy policy, LeaveCalendar leave) {
        this.ladder = ladder;
        this.policy = policy;
        this.leave = leave;
    }

    public SeniorityLadder ladder() {
        return ladder;
    }

    public DutyPolicy policy() {
        return policy;
    }

    /**
     * Why this member cannot take this duty, or empty when they can.
     *
     * @param alreadyCovered the sittings they are already down for in this plan
     * @param dutiesSoFar    how many duties they already hold in this plan
     */
    public Optional<String> refusal(Faculty member, ExamSlot slot,
                                    List<ExamSlot> alreadyCovered, int dutiesSoFar) {
        if (alreadyCovered.stream().anyMatch(slot::overlaps)) {
            return Optional.of("already invigilating another paper at that time");
        }
        if (leave.isAway(member.getId(), slot)) {
            return Optional.of("on confirmed leave at that time");
        }
        if (policy.weightBySeniority() && !carriesDuties(member)) {
            return Optional.of("their rank is configured to carry no invigilation");
        }
        if (policy.hasCap() && dutiesSoFar >= policy.maxDutiesPerMember()) {
            return Optional.of("already at the configured cap of " + policy.maxDutiesPerMember() + " duties");
        }
        if (!policy.courseTeacherMayInvigilate() && member.teaches(slot.getPaperCode())) {
            return Optional.of("teaches " + slot.getPaperCode());
        }
        return Optional.empty();
    }

    public boolean isEligible(Faculty member, ExamSlot slot, List<ExamSlot> alreadyCovered, int dutiesSoFar) {
        return refusal(member, slot, alreadyCovered, dutiesSoFar).isEmpty();
    }

    /**
     * The order duties should be offered in: least relatively loaded first.
     *
     * <p>The second key is what makes a weighted ladder work from the first room rather than
     * only from the second. At the start of a session nobody has any duties, so every relative
     * load is zero and the ordering falls to the tie-break — and a tie-break on id alone would
     * hand the first rooms to whoever's id sorts first, senior or not. Breaking the tie on the
     * share each rank is expected to carry asks the full-share colleagues first, which is what
     * "ask less of senior staff" has to mean in a small session where everybody gets one duty
     * or none.
     *
     * <p>Id is still the last key, so the result stays identical from run to run. With weighting
     * off every share is one, the second key never decides anything, and the order is exactly
     * what it was before any of this existed.
     */
    public Comparator<Faculty> leastLoadedFirst(ToIntFunction<Faculty> dutiesHeld) {
        return Comparator
                .comparingDouble((Faculty member) -> relativeLoad(member, dutiesHeld.applyAsInt(member)))
                .thenComparingDouble(member -> -expectedShareOf(member))
                .thenComparing(Faculty::getId);
    }

    /** The share this member is expected to carry, or one when weighting is off. */
    public double expectedShareOf(Faculty member) {
        return policy.weightBySeniority() ? ladder.dutyShareOf(member) : 1.0;
    }

    /**
     * How loaded this member is relative to what their rank is expected to carry.
     *
     * <p>Dividing duties by the expected share is what makes a weighted ladder fair rather than
     * merely lopsided: a professor on a half share reaches the same relative load after one duty
     * as an assistant professor does after two, so the allocator's "least loaded" question stays
     * meaningful across ranks. With weighting off, every share is one and this is just a count.
     */
    public double relativeLoad(Faculty member, int duties) {
        if (!policy.weightBySeniority()) {
            return duties;
        }
        double share = ladder.dutyShareOf(member);
        return share <= 0 ? Double.MAX_VALUE : duties / share;
    }

    /** The duties a full session would give this member at an even spread, for the fairness view. */
    public double fairShareOf(Faculty member, int totalDuties, double totalShares) {
        if (totalShares <= 0) {
            return 0;
        }
        double share = policy.weightBySeniority() ? ladder.dutyShareOf(member) : 1.0;
        return totalDuties * share / totalShares;
    }

    private boolean carriesDuties(Faculty member) {
        return ladder.dutyShareOf(member) > 0;
    }
}
