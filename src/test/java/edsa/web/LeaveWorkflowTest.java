package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.AbsenceWindow;
import edsa.core.Assignment;
import edsa.core.LeaveRequest;
import edsa.core.LeaveStatus;
import edsa.data.LeaveRequestRepository;
import edsa.data.SampleData;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The review deadline, and the statuses a request may move between.
 *
 * <p>The deadline is a prompt, never a decision. These tests pin down the thing that matters
 * most about it: a deadline going by does not approve anything.
 */
@SpringBootTest
@ActiveProfiles("test")
class LeaveWorkflowTest {

    @Autowired
    private LeaveWorkflow workflow;

    @Autowired
    private LeaveRequestRepository requests;

    @Autowired
    private Workspace workspace;

    @Autowired
    private LeaveProperties properties;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void loadAPlanAndForgetEveryRequest() {
        jdbc.update("DELETE FROM leave_request");
        workspace.setData(SampleData.load());
    }

    private String someoneOnDuty() {
        return workspace.result().plan().duties().all().get(0).facultyId();
    }

    private List<String> dutiesOf(String facultyId) {
        return workspace.result().plan().duties().forFaculty(facultyId).stream()
                .map(Assignment::slotId).sorted().toList();
    }

    /** A request whose review deadline has already gone by. */
    private LeaveRequest alreadyOverdue(String facultyId) {
        LeaveRequest stale = LeaveRequest.pending(facultyId, "Dr Overdue", AbsenceWindow.wholeSession(),
                "Reported sick", LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(2));
        return requests.record(stale);
    }

    // ── The deadline ──────────────────────────────────────────────────────────────────────

    @Test
    void theDefaultDeadlineComesFromConfiguration() {
        LocalDateTime deadline = workflow.defaultDeadline();

        assertEquals(24, properties.getConfirmWithinHours(), "the shipped default");
        assertTrue(deadline.isAfter(LocalDateTime.now().plusHours(23)), String.valueOf(deadline));
        assertTrue(deadline.isBefore(LocalDateTime.now().plusHours(25)), String.valueOf(deadline));
    }

    @Test
    void aDeadlineMayBeSetExplicitlyForOneRequest() {
        LocalDateTime deadline = LocalDateTime.now().plusHours(3).withNano(0);

        LeaveRequest recorded = workflow.record(someoneOnDuty(), AbsenceWindow.wholeSession(), null, deadline);

        assertEquals(deadline, recorded.confirmBy());
    }

    @Test
    void aDeadlineInThePastIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> workflow.record(someoneOnDuty(),
                AbsenceWindow.wholeSession(), null, LocalDateTime.now().minusHours(1)));
        assertTrue(requests.all().isEmpty());
    }

    @Test
    void aMissedDeadlineIsFlaggedForReviewAndNothingElse() {
        String away = someoneOnDuty();
        List<String> before = dutiesOf(away);
        LeaveRequest overdue = alreadyOverdue(away);

        LeaveRequest reloaded = requests.find(overdue.id()).orElseThrow();

        assertEquals(LeaveStatus.PENDING, reloaded.status(), "an unreviewed request is still pending");
        assertTrue(reloaded.isOverdueForReview(LocalDateTime.now()), "and it is flagged as overdue");
        assertTrue(reloaded.status().isOpen());
        assertFalse(reloaded.mayReassign(), "silence is never approval");
        assertEquals(before, dutiesOf(away), "no duty moved when the deadline went by");
    }

    @Test
    void anOverdueRequestCanStillBeDecided() {
        String away = someoneOnDuty();
        LeaveRequest overdue = alreadyOverdue(away);

        LeaveRequest decided = workflow.confirmAndReassign(overdue.id(), "admin@thapar.edu");

        assertEquals(LeaveStatus.COMPLETED, decided.status());
        assertTrue(dutiesOf(away).isEmpty(), "confirming late still covers the rooms");
    }

    @Test
    void aRequestInsideItsDeadlineIsNotOverdue() {
        LeaveRequest recorded = workflow.record(someoneOnDuty(), AbsenceWindow.wholeSession(), null, null);

        assertFalse(recorded.isOverdueForReview(LocalDateTime.now()));
        assertTrue(recorded.isOverdueForReview(recorded.confirmBy().plusSeconds(1)),
                "it becomes overdue once the deadline passes");
    }

    // ── Statuses ──────────────────────────────────────────────────────────────────────────

    @Test
    void onlyAConfirmedRequestMayMoveADuty() {
        assertFalse(LeaveStatus.PENDING.mayReassign());
        assertFalse(LeaveStatus.REJECTED.mayReassign());
        assertTrue(LeaveStatus.CONFIRMED.mayReassign());
        assertFalse(LeaveStatus.COMPLETED.mayReassign(), "its duties have already moved");
    }

    @Test
    void aRejectedRequestCannotThenBeConfirmed() {
        String away = someoneOnDuty();
        List<String> before = dutiesOf(away);
        LeaveRequest recorded = workflow.record(away, AbsenceWindow.wholeSession(), null, null);
        workflow.reject(recorded.id(), "admin@thapar.edu");

        assertThrows(IllegalArgumentException.class,
                () -> workflow.confirmAndReassign(recorded.id(), "admin@thapar.edu"));
        assertEquals(LeaveStatus.REJECTED, requests.find(recorded.id()).orElseThrow().status());
        assertEquals(before, dutiesOf(away));
    }

    @Test
    void aConfirmedRequestCannotThenBeRejected() {
        LeaveRequest recorded = workflow.record(someoneOnDuty(), AbsenceWindow.wholeSession(), null, null);
        workflow.confirmAndReassign(recorded.id(), "admin@thapar.edu");

        assertThrows(IllegalArgumentException.class,
                () -> workflow.reject(recorded.id(), "admin@thapar.edu"));
    }

    @Test
    void anUnknownRequestIsReportedNotIgnored() {
        assertThrows(IllegalArgumentException.class,
                () -> workflow.confirmAndReassign(987654, "admin@thapar.edu"));
        assertThrows(IllegalArgumentException.class, () -> workflow.reject(987654, "admin@thapar.edu"));
    }

    @Test
    void theOutcomeRecordsWhatTheConfirmationDid() {
        String away = someoneOnDuty();
        int moving = dutiesOf(away).size();
        LeaveRequest recorded = workflow.record(away, AbsenceWindow.wholeSession(), null, null);

        LeaveRequest decided = workflow.confirmAndReassign(recorded.id(), "admin@thapar.edu");

        assertTrue(decided.outcome().contains(String.valueOf(moving)), decided.outcome());
        assertTrue(decided.outcome().contains("reassigned"), decided.outcome());
    }

    @Test
    void aRejectionSaysSoInItsOutcome() {
        LeaveRequest recorded = workflow.record(someoneOnDuty(), AbsenceWindow.wholeSession(), null, null);

        LeaveRequest decided = workflow.reject(recorded.id(), "admin@thapar.edu");

        assertTrue(decided.outcome().contains("roster was not changed"), decided.outcome());
    }

    @Test
    void anAbsenceForSomebodyNotInTheLoadedDataIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> workflow.record("NOBODY", AbsenceWindow.wholeSession(), null, null));
    }

    @Test
    void aConfirmedAbsenceKeepsSomebodyOffDutyForALaterRepair() {
        String away = someoneOnDuty();
        LeaveRequest recorded = workflow.record(away, AbsenceWindow.wholeSession(), null, null);
        workflow.confirmAndReassign(recorded.id(), "admin@thapar.edu");

        assertTrue(requests.confirmedAbsences().stream()
                        .anyMatch(absence -> absence.facultyId().equals(away)),
                "so a second absence is never covered by somebody already away");
    }
}
