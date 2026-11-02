package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.Assignment;
import edsa.core.DutyOrigin;
import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.SubstitutionRequest;
import edsa.core.SubstitutionStatus;
import edsa.data.SampleData;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The substitution lifecycle against a real database, because the protections that matter are
 * the ones the database enforces: one open offer per sitting, one award per substitution, and no
 * covering your own duty.
 */
@SpringBootTest
@ActiveProfiles("test")
class SubstitutionWorkflowTest {

    @Autowired
    private RewardWorkflow rewards;

    @Autowired
    private Workspace workspace;

    @Autowired
    private JdbcTemplate jdbc;

    private String ownerId;
    private String coverId;
    private String slotId;
    private String roomId;

    private static final RewardWorkflow.Actor ADMIN =
            new RewardWorkflow.Actor("boss@thapar.edu", null, true);

    private RewardWorkflow.Actor owner() {
        return new RewardWorkflow.Actor(ownerId.toLowerCase() + "@thapar.edu", ownerId, false);
    }

    private RewardWorkflow.Actor cover() {
        return new RewardWorkflow.Actor(coverId.toLowerCase() + "@thapar.edu", coverId, false);
    }

    @BeforeEach
    void planASession() {
        jdbc.update("DELETE FROM points_transaction");
        jdbc.update("DELETE FROM duty_redemption");
        jdbc.update("DELETE FROM substitution_request");
        jdbc.update("DELETE FROM duty_decision");
        jdbc.update("DELETE FROM exam_time_override");
        jdbc.update("DELETE FROM exam_time_change");
        jdbc.update("DELETE FROM leave_request");

        ExamData data = SampleData.load();
        workspace.setData(data);
        workspace.compute();

        Assignment duty = workspace.result().plan().duties().all().get(0);
        ownerId = duty.facultyId();
        slotId = duty.slotId();
        roomId = duty.roomId();
        coverId = data.faculty().stream()
                .map(member -> member.getId())
                .filter(id -> !id.equals(ownerId))
                .filter(id -> workspace.result().plan().duties().forFaculty(id).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the sample needs a spare invigilator"));
    }

    private SubstitutionRequest offered() {
        return rewards.offer(owner(), slotId, roomId, null, "Conference");
    }

    @Test
    void aProfessorMayOnlyOfferTheirOwnDuty() {
        RewardWorkflow.Actor somebodyElse =
                new RewardWorkflow.Actor("other@thapar.edu", coverId, false);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.offer(somebodyElse, slotId, roomId, null, null));
        assertTrue(refused.getMessage().contains("belongs to " + ownerId), refused.getMessage());
    }

    @Test
    void anAccountWithNoFacultyRecordHasNothingToOffer() {
        RewardWorkflow.Actor unlinked = new RewardWorkflow.Actor("nobody@thapar.edu", null, false);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.offer(unlinked, slotId, roomId, null, null));
        assertTrue(refused.getMessage().contains("not linked to a faculty record"));
    }

    @Test
    void theSameSittingCannotBeOfferedTwiceWhileAnOfferIsOpen() {
        offered();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, this::offered);
        assertTrue(refused.getMessage().contains("already been offered"));
    }

    @Test
    void coveringYourOwnDutyIsRefused() {
        SubstitutionRequest request = offered();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.volunteer(owner(), request.id()));
        assertTrue(refused.getMessage().contains("cannot cover your own duty"));
    }

    @Test
    void onlyTheFirstVolunteerGetsIt() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());

        String thirdId = workspace.data().faculty().stream()
                .map(member -> member.getId())
                .filter(id -> !id.equals(ownerId) && !id.equals(coverId))
                .findFirst().orElseThrow();
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.volunteer(new RewardWorkflow.Actor("third@thapar.edu", thirdId, false),
                        request.id()));
        assertTrue(refused.getMessage().contains("no longer open"), refused.getMessage());
    }

    @Test
    void approvalMovesTheDutyAndAwardsNothing() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());

        rewards.approveSubstitution(ADMIN, request.id());

        assertEquals(0, rewards.balanceOf(coverId).earned(),
                "approval is agreement to turn up, not having turned up");
        Assignment moved = workspace.result().plan().duties().forSitting(slotId, roomId).orElseThrow();
        assertEquals(coverId, moved.facultyId());
        assertEquals(DutyOrigin.SUBSTITUTED, moved.origin());
    }

    @Test
    void pointsAreAwardedOnlyWhenTheDutyIsConfirmedDone() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());
        rewards.approveSubstitution(ADMIN, request.id());

        int awarded = rewards.completeAndAward(ADMIN, request.id());

        assertEquals(rewards.rules().pointsPerSubstitution(), awarded);
        assertEquals(rewards.rules().pointsPerSubstitution(), rewards.balanceOf(coverId).available());
        assertEquals(1, rewards.ledgerFor(coverId).size());
    }

    @Test
    void aSubstitutionCannotBeCompletedTwice() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());
        rewards.approveSubstitution(ADMIN, request.id());
        rewards.completeAndAward(ADMIN, request.id());

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.completeAndAward(ADMIN, request.id()));
        assertTrue(refused.getMessage().contains("approved substitution"), refused.getMessage());
        assertEquals(rewards.rules().pointsPerSubstitution(), rewards.balanceOf(coverId).earned(),
                "a second completion must not mint a second award");
    }

    @Test
    void anUnapprovedSubstitutionCannotBeCompleted() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());

        assertThrows(IllegalArgumentException.class,
                () -> rewards.completeAndAward(ADMIN, request.id()));
        assertEquals(0, rewards.balanceOf(coverId).earned());
    }

    @Test
    void nobodyDecidesACaseTheyArePartyTo() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());

        RewardWorkflow.Actor adminWhoIsAlsoTheVolunteer =
                new RewardWorkflow.Actor("boss@thapar.edu", coverId, true);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.approveSubstitution(adminWhoIsAlsoTheVolunteer, request.id()));
        assertTrue(refused.getMessage().contains("party to"), refused.getMessage());
    }

    @Test
    void anAdministratorCannotConfirmTheirOwnSubstituteDuty() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());
        rewards.approveSubstitution(ADMIN, request.id());

        RewardWorkflow.Actor adminWhoCovered =
                new RewardWorkflow.Actor("other-admin@thapar.edu", coverId, true);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> rewards.completeAndAward(adminWhoCovered, request.id()));
        assertTrue(refused.getMessage().contains("your own substitute duty"), refused.getMessage());
    }

    @Test
    void aProfessorMayNotApproveAnything() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());

        assertThrows(IllegalArgumentException.class,
                () -> rewards.approveSubstitution(cover(), request.id()));
        assertThrows(IllegalArgumentException.class,
                () -> rewards.rejectSubstitution(cover(), request.id(), "no"));
    }

    @Test
    void rejectingLeavesTheDutyWhereItWas() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());

        rewards.rejectSubstitution(ADMIN, request.id(), "Needed elsewhere");

        Assignment unmoved = workspace.result().plan().duties().forSitting(slotId, roomId).orElseThrow();
        assertEquals(ownerId, unmoved.facultyId());
        assertEquals(0, rewards.balanceOf(coverId).earned());
    }

    @Test
    void theOwnerMayTakeAnUndecidedOfferBack() {
        SubstitutionRequest request = offered();

        rewards.withdraw(owner(), request.id());

        assertEquals(SubstitutionStatus.WITHDRAWN,
                rewards.allSubstitutions().get(0).status());
        // The sitting is free to be offered again, which is what the open-offer key allows.
        assertEquals(SubstitutionStatus.OFFERED, offered().status());
    }

    @Test
    void somebodyElseCannotWithdrawYourOffer() {
        SubstitutionRequest request = offered();

        assertThrows(IllegalArgumentException.class, () -> rewards.withdraw(cover(), request.id()));
    }

    @Test
    void aCorrectionMustSayWhyAndCannotBeYourOwn() {
        RewardWorkflow.Actor adminWithRecord =
                new RewardWorkflow.Actor("boss@thapar.edu", coverId, true);

        assertThrows(IllegalArgumentException.class,
                () -> rewards.adjust(ADMIN, coverId, 10, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> rewards.adjust(adminWithRecord, coverId, 10, "a reason"));
        assertThrows(IllegalArgumentException.class,
                () -> rewards.adjust(cover(), coverId, 10, "a reason"));

        rewards.adjust(ADMIN, coverId, 25, "Covered two sessions last term");
        assertEquals(25, rewards.balanceOf(coverId).available());
    }

    @Test
    void theSubstitutionRecordOutlivesThePlanItWasMadeAgainst() {
        SubstitutionRequest request = offered();
        rewards.volunteer(cover(), request.id());
        rewards.approveSubstitution(ADMIN, request.id());
        rewards.completeAndAward(ADMIN, request.id());

        // A fresh plan: the workbook was reimported, everything recomputed.
        workspace.setData(SampleData.load());

        List<SubstitutionRequest> history = rewards.substitutionsFor(coverId);
        assertFalse(history.isEmpty(), "who covered what must still be readable");
        assertEquals(SubstitutionStatus.COMPLETED, history.get(0).status());
        assertEquals(rewards.rules().pointsPerSubstitution(), rewards.balanceOf(coverId).available());
    }
}
