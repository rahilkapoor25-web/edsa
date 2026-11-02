package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.Assignment;
import edsa.core.DutyOrigin;
import edsa.core.DutyRedemption;
import edsa.core.RedemptionStatus;
import edsa.data.SampleData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spending points on an exemption, through the database and the real plan.
 *
 * <p>The sample session gives everybody at most one duty and the configured floor is one, so the
 * ordinary case here is a refusal — which is the right default and is tested as such. The grant
 * path is reached by moving duties onto one person first, because somebody has to have a duty to
 * spare before giving one up can possibly be safe.
 */
@SpringBootTest
@ActiveProfiles("test")
class RedemptionWorkflowTest {

    @Autowired
    private RewardWorkflow rewards;

    @Autowired
    private Workspace workspace;

    @Autowired
    private JdbcTemplate jdbc;

    private static final RewardWorkflow.Actor ADMIN =
            new RewardWorkflow.Actor("boss@thapar.edu", null, true);

    private RewardWorkflow.Actor professor(String facultyId) {
        return new RewardWorkflow.Actor(facultyId.toLowerCase() + "@thapar.edu", facultyId, false);
    }

    @BeforeEach
    void planASession() {
        jdbc.update("DELETE FROM points_transaction");
        jdbc.update("DELETE FROM duty_redemption");
        jdbc.update("DELETE FROM substitution_request");
        jdbc.update("DELETE FROM duty_decision");
        workspace.setData(SampleData.load());
        workspace.compute();
    }

    /** Moves every duty onto one person, so they have one to spare. */
    private String pileDutiesOnto(String facultyId) {
        for (Assignment duty : java.util.List.copyOf(workspace.result().plan().duties().all())) {
            workspace.result().plan().duties()
                    .reassign(duty.slotId(), duty.roomId(), facultyId, DutyOrigin.MANUAL);
        }
        return facultyId;
    }

    private void giveThemPoints(String facultyId, int points) {
        rewards.adjust(ADMIN, facultyId, points, "Opening balance for this test");
    }

    @Test
    void withoutEnoughPointsTheRequestIsRefusedBeforeItIsStored() {
        Assignment duty = workspace.result().plan().duties().all().get(0);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.requestRelief(professor(duty.facultyId()), duty.slotId(), duty.roomId(), null));

        assertTrue(refused.getMessage().contains("points"), refused.getMessage());
        assertTrue(rewards.allRedemptions().isEmpty(), "a refused request must not be stored");
    }

    @Test
    void theFloorRefusesEvenSomebodyWithPlentyOfPoints() {
        Assignment duty = workspace.result().plan().duties().all().get(0);
        giveThemPoints(duty.facultyId(), 500);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.requestRelief(professor(duty.facultyId()), duty.slotId(), duty.roomId(), null));

        assertTrue(refused.getMessage().contains("below the minimum"), refused.getMessage());
    }

    @Test
    void grantingSpendsThePointsAndMovesTheDutyToANamedColleague() {
        String busy = pileDutiesOnto(workspace.data().faculty().get(0).getId());
        giveThemPoints(busy, 100);
        Assignment giveUp = workspace.result().plan().duties().forFaculty(busy).get(0);

        DutyRedemption request = rewards.requestRelief(professor(busy),
                giveUp.slotId(), giveUp.roomId(), "Family");
        assertEquals(RedemptionStatus.PENDING, request.status());
        assertEquals(rewards.rules().pointsToSkipOneDuty(), rewards.balanceOf(busy).pending(),
                "the points are held, not yet spent");

        rewards.approveRelief(ADMIN, request.id());

        assertEquals(100 - rewards.rules().pointsToSkipOneDuty(), rewards.balanceOf(busy).available());
        assertEquals(0, rewards.balanceOf(busy).pending());
        Assignment moved = workspace.result().plan().duties()
                .forSitting(giveUp.slotId(), giveUp.roomId()).orElseThrow();
        assertFalse(moved.facultyId().equals(busy), "somebody else must now hold the room");
        assertEquals(DutyOrigin.SUBSTITUTED, moved.origin());
    }

    @Test
    void refusingReleasesTheHeldPoints() {
        String busy = pileDutiesOnto(workspace.data().faculty().get(0).getId());
        giveThemPoints(busy, 100);
        Assignment giveUp = workspace.result().plan().duties().forFaculty(busy).get(0);
        DutyRedemption request = rewards.requestRelief(professor(busy),
                giveUp.slotId(), giveUp.roomId(), null);

        rewards.rejectRelief(ADMIN, request.id(), "Short-staffed that morning");

        assertEquals(100, rewards.balanceOf(busy).available(), "nothing was spent");
        assertEquals(0, rewards.balanceOf(busy).pending());
    }

    @Test
    void theSameRequestCannotBeApprovedTwice() {
        String busy = pileDutiesOnto(workspace.data().faculty().get(0).getId());
        giveThemPoints(busy, 200);
        Assignment giveUp = workspace.result().plan().duties().forFaculty(busy).get(0);
        DutyRedemption request = rewards.requestRelief(professor(busy),
                giveUp.slotId(), giveUp.roomId(), null);
        rewards.approveRelief(ADMIN, request.id());
        int spentOnce = rewards.balanceOf(busy).redeemed();

        assertThrows(IllegalArgumentException.class, () -> rewards.approveRelief(ADMIN, request.id()));
        assertEquals(spentOnce, rewards.balanceOf(busy).redeemed(),
                "a second approval must not spend the points again");
    }

    @Test
    void aProfessorMayNotDecideTheirOwnRequest() {
        String busy = pileDutiesOnto(workspace.data().faculty().get(0).getId());
        giveThemPoints(busy, 100);
        Assignment giveUp = workspace.result().plan().duties().forFaculty(busy).get(0);
        DutyRedemption request = rewards.requestRelief(professor(busy),
                giveUp.slotId(), giveUp.roomId(), null);

        assertThrows(IllegalArgumentException.class,
                () -> rewards.approveRelief(professor(busy), request.id()));

        RewardWorkflow.Actor adminWhoAsked =
                new RewardWorkflow.Actor("boss@thapar.edu", busy, true);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.approveRelief(adminWhoAsked, request.id()));
        assertTrue(refused.getMessage().contains("your own request"), refused.getMessage());
    }

    @Test
    void thePendingHoldStopsTheSameThirtyPointsBuyingTwoExemptions() {
        String busy = pileDutiesOnto(workspace.data().faculty().get(0).getId());
        giveThemPoints(busy, rewards.rules().pointsToSkipOneDuty());
        var duties = workspace.result().plan().duties().forFaculty(busy);
        rewards.requestRelief(professor(busy), duties.get(0).slotId(), duties.get(0).roomId(), null);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.requestRelief(professor(busy),
                        duties.get(1).slotId(), duties.get(1).roomId(), null));
        assertTrue(refused.getMessage().contains("only 0 are available"), refused.getMessage());
    }

    /**
     * The guard runs again at the decision, not only at the request.
     *
     * <p>The reason it refuses is deliberately not asserted here: the plan is recomputed between
     * the two, so the specific objection depends on what the new plan looks like. What matters is
     * that a stale request is refused rather than granted, that nothing is spent, and that it
     * stays open. {@link edsa.core.DutyReliefTest} covers each individual reason exactly.
     */
    @Test
    void aGrantIsRefusedIfNobodyIsFreeByTheTimeItIsDecided() {
        String busy = pileDutiesOnto(workspace.data().faculty().get(0).getId());
        giveThemPoints(busy, 100);
        Assignment giveUp = workspace.result().plan().duties().forFaculty(busy).get(0);
        DutyRedemption request = rewards.requestRelief(professor(busy),
                giveUp.slotId(), giveUp.roomId(), null);

        // Everybody else becomes unavailable between the asking and the deciding: the data is
        // reloaded with a single invigilator, so there is nobody left to stand in.
        edsa.core.ExamData onlyOne = new edsa.core.ExamData(
                workspace.data().students(), workspace.data().rooms(),
                java.util.List.of(workspace.data().facultyMember(busy)), workspace.data().exams());
        workspace.setData(onlyOne);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.approveRelief(ADMIN, request.id()));
        assertTrue(refused.getMessage().contains("cannot be granted at the moment"),
                refused.getMessage());

        // Nothing was spent, and the request is still waiting rather than silently thrown away:
        // who is free changes, so this may be grantable later, and the administrator can refuse
        // it outright if it never will be.
        assertEquals(0, rewards.balanceOf(busy).redeemed(), "a refused grant must not spend points");
        assertEquals(RedemptionStatus.PENDING, rewards.allRedemptions().get(0).status());
        assertEquals(rewards.rules().pointsToSkipOneDuty(), rewards.balanceOf(busy).pending(),
                "the points stay held while the request is still open");
    }
}
