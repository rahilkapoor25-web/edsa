package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.Assignment;
import edsa.core.DutyOrigin;
import edsa.data.SampleData;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Reviewing the allocator's recommendations: approving them, overruling them, and the one
 * property that makes either worth doing — that a decision outlives the plan it was made about.
 */
@SpringBootTest
@ActiveProfiles("test")
class DutyApprovalTest {

    @Autowired
    private DutyWorkflow duties;

    @Autowired
    private Workspace workspace;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String WHO = "coord@thapar.edu";

    @BeforeEach
    void planASession() {
        jdbc.update("DELETE FROM duty_decision");
        jdbc.update("DELETE FROM exam_time_override");
        jdbc.update("DELETE FROM leave_request");
        workspace.setData(SampleData.load());
        workspace.compute();
    }

    private Assignment firstDuty() {
        return workspace.result().plan().duties().all().get(0);
    }

    private DutyOrigin originOf(String slotId, String roomId) {
        return workspace.result().plan().duties().forSitting(slotId, roomId)
                .map(Assignment::origin).orElseThrow();
    }

    @Test
    void everyDutyStartsAsARecommendationNobodyHasLookedAt() {
        assertTrue(workspace.result().plan().duties().all().stream()
                .allMatch(duty -> duty.origin() == DutyOrigin.RECOMMENDED));
        assertEquals(0, workspace.result().plan().duties().decidedCount());
        assertTrue(duties.rows().stream().allMatch(DutyWorkflow.DutyRow::awaitingReview));
    }

    @Test
    void approvingOneLeavesTheInvigilatorAloneAndMarksItDecided() {
        Assignment duty = firstDuty();

        duties.approve(duty.slotId(), duty.roomId(), WHO);

        assertEquals(DutyOrigin.APPROVED, originOf(duty.slotId(), duty.roomId()));
        assertEquals(duty.facultyId(),
                workspace.result().plan().duties().forSitting(duty.slotId(), duty.roomId())
                        .orElseThrow().facultyId());
        assertEquals(1, duties.decisionCount());
    }

    /**
     * The whole point of storing decisions. Approving a roster and then reloading the workbook
     * used to throw the approvals away, which made approving pointless.
     */
    @Test
    void anApprovalSurvivesARecomputeAndAReimport() {
        Assignment duty = firstDuty();
        duties.approve(duty.slotId(), duty.roomId(), WHO);

        workspace.compute();
        assertEquals(DutyOrigin.APPROVED, originOf(duty.slotId(), duty.roomId()));

        workspace.setData(SampleData.load());
        assertEquals(DutyOrigin.APPROVED, originOf(duty.slotId(), duty.roomId()));
        assertEquals(duty.facultyId(),
                workspace.result().plan().duties().forSitting(duty.slotId(), duty.roomId())
                        .orElseThrow().facultyId());
    }

    @Test
    void approvingEverythingIsOneTransactionAndLeavesNothingWaiting() {
        int rooms = workspace.result().plan().duties().all().size();

        int approved = duties.approveAllRecommended(WHO);

        assertEquals(rooms, approved);
        assertEquals(rooms, workspace.result().plan().duties().decidedCount());
        assertTrue(duties.rows().stream().noneMatch(DutyWorkflow.DutyRow::awaitingReview));
        assertEquals(0, duties.approveAllRecommended(WHO), "a second run has nothing left to do");
    }

    @Test
    void aCoordinatorMayOverruleTheAllocatorForOneRoom() {
        Assignment duty = firstDuty();
        String somebodyElse = workspace.data().faculty().stream()
                .map(member -> member.getId())
                .filter(id -> !id.equals(duty.facultyId()))
                .filter(id -> workspace.result().plan().duties().forFaculty(id).isEmpty())
                .findFirst().orElseThrow();

        duties.assignByHand(duty.slotId(), duty.roomId(), somebodyElse, WHO, "Asked to swap");

        Assignment now = workspace.result().plan().duties()
                .forSitting(duty.slotId(), duty.roomId()).orElseThrow();
        assertEquals(somebodyElse, now.facultyId());
        assertEquals(DutyOrigin.MANUAL, now.origin());
    }

    /**
     * A coordinator may overrule the allocator's preference, but not produce a plan that fails
     * its own rule check: somebody cannot be in two rooms at nine o'clock.
     *
     * <p>The scenario is built here rather than looked for in the sample, which puts one room on
     * each sitting. A test that quietly skips itself when the data does not happen to contain the
     * case it is named after is worse than no test at all.
     */
    @Test
    void aHandAssignmentThatBreaksARuleIsRefusedInTheCoresOwnWords() {
        workspace.setData(twoRoomsOneSitting());

        Assignment inRoomOne = workspace.result().plan().duties().forSitting("E1", "R1").orElseThrow();
        Assignment inRoomTwo = workspace.result().plan().duties().forSitting("E1", "R2").orElseThrow();
        assertFalse(inRoomOne.facultyId().equals(inRoomTwo.facultyId()),
                "the two parallel rooms should have different invigilators to begin with");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> duties.assignByHand("E1", "R1", inRoomTwo.facultyId(), WHO, null));

        assertTrue(refused.getMessage().contains("already invigilating another paper at that time"),
                refused.getMessage());
        assertEquals(inRoomOne.facultyId(),
                workspace.result().plan().duties().forSitting("E1", "R1").orElseThrow().facultyId(),
                "a refused assignment must leave the room as it was");
    }

    /** One paper filling two rooms at once, invigilated by two people who teach nothing. */
    private edsa.core.ExamData twoRoomsOneSitting() {
        java.util.List<edsa.core.Student> cohort = new java.util.ArrayList<>();
        for (int index = 1; index <= 8; index++) {
            cohort.add(new edsa.core.Student("X" + index, "Candidate " + index, "CSE",
                    java.util.Set.of("XX999")));
        }
        return new edsa.core.ExamData(
                cohort,
                List.of(edsa.core.Room.ofCapacity("R1", "Room 1", 4, "Block A"),
                        edsa.core.Room.ofCapacity("R2", "Room 2", 4, "Block A")),
                List.of(new edsa.core.Faculty("FA", "Invigilator A", "CSE", false, java.util.Set.of()),
                        new edsa.core.Faculty("FB", "Invigilator B", "CSE", false, java.util.Set.of())),
                List.of(new edsa.core.Exam("E1", "XX999", "A paper", java.time.LocalDate.of(2026, 11, 10),
                        java.time.LocalTime.of(9, 0), null)));
    }

    @Test
    void aHandAssignmentToSomebodyWhoDoesNotExistIsRefused() {
        Assignment duty = firstDuty();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> duties.assignByHand(duty.slotId(), duty.roomId(), "NOBODY", WHO, null));
        assertTrue(refused.getMessage().contains("no faculty record"));
    }

    @Test
    void aDecisionCanBeTakenBackReturningTheRoomToTheRecommendation() {
        Assignment duty = firstDuty();
        duties.approve(duty.slotId(), duty.roomId(), WHO);

        duties.clearDecision(duty.slotId(), duty.roomId());

        assertEquals(DutyOrigin.RECOMMENDED, originOf(duty.slotId(), duty.roomId()));
        assertEquals(0, duties.decisionCount());
    }

    @Test
    void decidingTwiceAboutOneRoomReplacesTheEarlierDecision() {
        Assignment duty = firstDuty();
        String somebodyElse = workspace.data().faculty().stream()
                .map(member -> member.getId())
                .filter(id -> !id.equals(duty.facultyId()))
                .filter(id -> workspace.result().plan().duties().forFaculty(id).isEmpty())
                .findFirst().orElseThrow();

        duties.approve(duty.slotId(), duty.roomId(), WHO);
        duties.assignByHand(duty.slotId(), duty.roomId(), somebodyElse, WHO, null);

        assertEquals(1, duties.decisionCount(), "one decision per room, not a pile of them");
        assertEquals(somebodyElse, workspace.result().plan().duties()
                .forSitting(duty.slotId(), duty.roomId()).orElseThrow().facultyId());
    }

    @Test
    void theRowsIncludeARoomWithNobodyInIt() {
        // One invigilator, two parallel rooms: the second room cannot be staffed and has to be
        // visible, because an unstaffed room is the most important row on the page.
        edsa.core.ExamData shortStaffed = new edsa.core.ExamData(
                workspace.data().students(),
                workspace.data().rooms(),
                List.of(workspace.data().faculty().get(0)),
                workspace.data().exams());
        workspace.setData(shortStaffed);

        List<DutyWorkflow.DutyRow> rows = duties.rows();
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().anyMatch(DutyWorkflow.DutyRow::isUnstaffed),
                "a room with students and no invigilator must appear");
    }

    @Test
    void nothingCanBeApprovedBeforeThereIsAPlan() {
        workspace.setData(null);

        assertThrows(IllegalArgumentException.class, () -> duties.approve("E1", "R1", WHO));
        assertThrows(IllegalArgumentException.class, () -> duties.approveAllRecommended(WHO));
        assertTrue(duties.rows().isEmpty());
    }
}
