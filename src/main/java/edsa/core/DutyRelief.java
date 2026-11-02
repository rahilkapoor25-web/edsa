package edsa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The guard on spending points: decides whether one duty can actually be given up.
 *
 * <p>This is the class that keeps a reward scheme from quietly becoming a staffing problem. A
 * credit is worth something only if it can be spent, and spending it is only safe if the room
 * is still invigilated afterwards — so the test is not "has this person got thirty points" but
 * "is there a specific named colleague who could stand in, and would letting this person off
 * leave them doing less than the minimum the office set". Both have to hold, and the colleague
 * has to be found rather than assumed, because "somebody will cover it" is how an examination
 * hall ends up with nobody in it.
 *
 * <p>Checked at the moment of decision and never cached. Who is free at nine on the tenth
 * depends on the plan as it stands, and the plan changes.
 */
public final class DutyRelief {

    private final DutyEligibility eligibility;
    private final RewardRules rules;

    public DutyRelief(DutyEligibility eligibility, RewardRules rules) {
        this.eligibility = eligibility;
        this.rules = rules;
    }

    /**
     * Whether this member may be excused this duty.
     *
     * @param plan                 the plan as it currently stands
     * @param facultyId            who is asking
     * @param slotId               the sitting
     * @param roomId               the room
     * @param balance              their points
     * @param redemptionsThisSession how many duties they have already been excused
     */
    public ReliefDecision check(ExamPlan plan, String facultyId, String slotId, String roomId,
                                PointsBalance balance, int redemptionsThisSession) {
        if (plan == null) {
            return ReliefDecision.refused("There is no plan to be excused a duty from");
        }
        ExamSlot slot = plan.slot(slotId);
        if (slot == null) {
            return ReliefDecision.refused("That sitting is not in the current plan");
        }
        boolean holdsDuty = plan.duties().all().stream()
                .anyMatch(duty -> duty.slotId().equals(slotId)
                        && duty.roomId().equals(roomId)
                        && duty.facultyId().equals(facultyId));
        if (!holdsDuty) {
            return ReliefDecision.refused("That duty does not belong to " + facultyId);
        }

        if (!balance.canAfford(rules.pointsToSkipOneDuty())) {
            return ReliefDecision.refused("Being excused a duty costs " + rules.pointsToSkipOneDuty()
                    + " points and only " + balance.available() + " are available");
        }
        if (rules.maxRedemptionsPerSession() > 0 && redemptionsThisSession >= rules.maxRedemptionsPerSession()) {
            return ReliefDecision.refused("Only " + rules.maxRedemptionsPerSession()
                    + " duty may be redeemed per session, and that has been used");
        }

        int dutiesHeld = plan.duties().forFaculty(facultyId).size();
        int floor = eligibility.policy().minDutiesPerMember();
        if (eligibility.policy().hasFloor() && dutiesHeld - 1 < floor) {
            return ReliefDecision.refused("It would leave " + facultyId + " with " + (dutiesHeld - 1)
                    + " duties, below the minimum of " + floor);
        }

        return standIn(plan, slot, roomId, facultyId)
                .map(ReliefDecision::allowedWith)
                .orElseGet(() -> ReliefDecision.refused("No colleague is free to take room " + roomId
                        + " during " + slot.getPaperCode() + ", so the room would be left unstaffed"));
    }

    /**
     * The colleague who would take this duty: the least loaded one who is eligible for it.
     *
     * <p>Chosen the same way the allocator chooses, so an exemption does not land the duty on
     * somebody the allocator would never have given it to.
     */
    public java.util.Optional<String> standIn(ExamPlan plan, ExamSlot slot, String roomId, String excluding) {
        List<Faculty> candidates = new ArrayList<>(plan.data().faculty());
        candidates.sort(eligibility.leastLoadedFirst(
                member -> plan.duties().forFaculty(member.getId()).size()));

        for (Faculty candidate : candidates) {
            if (candidate.getId().equals(excluding)) {
                continue;
            }
            List<ExamSlot> covered = plan.duties().forFaculty(candidate.getId()).stream()
                    .map(duty -> plan.slot(duty.slotId()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (eligibility.isEligible(candidate, slot, covered, covered.size())) {
                return java.util.Optional.of(candidate.getId());
            }
        }
        return java.util.Optional.empty();
    }

    public RewardRules rules() {
        return rules;
    }
}
