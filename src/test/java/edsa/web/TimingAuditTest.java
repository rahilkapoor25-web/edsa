package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.ExamMode;
import edsa.core.TimingOverride;
import edsa.core.TimingReview;
import edsa.data.SampleData;
import edsa.data.TimingChange;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Saving a timing change: the confirmation gate, the audit history, and the fact that the change
 * reaches the plan rather than only the table it was typed into.
 */
@SpringBootTest
@ActiveProfiles("test")
class TimingAuditTest {

    @Autowired
    private TimingWorkflow timings;

    @Autowired
    private Workspace workspace;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String WHO = "coord@thapar.edu";

    @BeforeEach
    void planASession() {
        jdbc.update("DELETE FROM exam_time_override");
        jdbc.update("DELETE FROM exam_time_change");
        jdbc.update("DELETE FROM duty_decision");
        workspace.setData(SampleData.load());
        workspace.compute();
    }

    private TimingWorkflow.PaperTiming paper(String examId) {
        return timings.papers().stream()
                .filter(row -> row.examId().equals(examId))
                .findFirst().orElseThrow();
    }

    @Test
    void aCleanChangeIsSavedAndRecorded() {
        TimingReview review = timings.save(
                new TimingOverride("E1", null, LocalTime.of(10, 0), null), WHO, false);

        assertTrue(review.isClean(), review.findings().toString());
        assertEquals(LocalTime.of(10, 0), paper("E1").start());
        assertTrue(paper("E1").overridden());

        List<TimingChange> history = timings.history(10);
        assertEquals(1, history.size());
        assertEquals("CS301", history.get(0).paperCode());
        assertEquals(WHO, history.get(0).changedBy());
        assertFalse(history.get(0).wasSavedWithWarnings());
        assertTrue(history.get(0).became().contains("10:00"), history.get(0).became());
    }

    @Test
    void aChangeThatWouldClashIsNotSavedUntilItIsConfirmed() {
        // CS302 is sat by the same cohort as CS301, so moving it alongside clashes.
        TimingOverride onto = new TimingOverride("E3", LocalDate.of(2026, 11, 10),
                LocalTime.of(9, 30), null);

        TimingReview first = timings.save(onto, WHO, false);

        assertTrue(first.hasWarnings(), "the clash should have been found");
        assertFalse(paper("E3").overridden(), "nothing may be written until somebody confirms");
        assertTrue(timings.history(10).isEmpty(), "and nothing goes in the history either");
    }

    @Test
    void confirmingSavesItAndRecordsTheWarningsThatWereShown() {
        TimingOverride onto = new TimingOverride("E3", LocalDate.of(2026, 11, 10),
                LocalTime.of(9, 30), null);

        TimingReview confirmed = timings.save(onto, WHO, true);

        assertTrue(confirmed.hasWarnings());
        assertTrue(paper("E3").overridden());
        TimingChange recorded = timings.history(10).get(0);
        assertTrue(recorded.wasSavedWithWarnings(),
                "whether a clash was created knowingly is the first thing anybody asks");
        assertTrue(recorded.warnings().contains("already sitting"), recorded.warnings());
    }

    @Test
    void aBlockedChangeIsNeverSavedHoweverMuchItIsConfirmed() {
        TimingReview review = timings.save(
                new TimingOverride("E1", null, null, 9999), WHO, true);

        assertTrue(review.isBlocked());
        assertFalse(paper("E1").overridden());
        assertTrue(timings.history(10).isEmpty());
    }

    @Test
    void anAuthorisedLengthReachesTheTimetableAndTheRuleCheck() {
        timings.save(new TimingOverride("E1", null, null, 45), WHO, true);

        assertEquals(45, paper("E1").minutes());
        assertEquals(45, workspace.result().plan().slot("E1").getDurationMinutes(),
                "the change has to reach the plan, not just the table it was typed into");
        assertTrue(workspace.result().checks().stream()
                        .filter(check -> check.constraintName().equals("Exam duration"))
                        .allMatch(check -> check.satisfied()),
                "and the duration rule must accept what was approved");
    }

    @Test
    void revertingPutsThePaperBackAndKeepsTheHistory() {
        timings.save(new TimingOverride("E1", null, LocalTime.of(11, 0), null), WHO, false);
        assertTrue(paper("E1").overridden());

        timings.revert("E1", WHO);

        assertFalse(paper("E1").overridden());
        assertEquals(2, timings.history(10).size(),
                "a revert is itself a change, and the change it undid is still on the record");
        assertTrue(timings.history(10).get(0).became().contains("as declared"));
    }

    @Test
    void movingAWholeDayIsAllOrNothing() {
        LocalDate day = paper("E1").date();
        long papersThatDay = timings.papers().stream().filter(row -> row.date().equals(day)).count();

        TimingWorkflow.SessionShift shift = timings.shiftSession(day, LocalTime.of(10, 0), null, WHO, true);

        assertTrue(shift.succeeded());
        assertEquals(papersThatDay, shift.moved());
        timings.papers().stream()
                .filter(row -> row.date().equals(day))
                .forEach(row -> assertEquals(LocalTime.of(10, 0), row.start(),
                        row.paperCode() + " should have moved with the rest of the day"));
    }

    @Test
    void aDayWithNoPapersMovesNothingAndSaysSo() {
        TimingWorkflow.SessionShift shift = timings.shiftSession(
                LocalDate.of(2030, 1, 1), LocalTime.of(10, 0), null, WHO, true);

        assertFalse(shift.succeeded());
        assertEquals(0, shift.moved());
        assertTrue(shift.blockers().get(0).contains("No paper is scheduled"));
    }

    @Test
    void aBlockedPaperStopsTheWholeDayMoving() {
        LocalDate day = paper("E1").date();

        // A length no mode accepts: every paper on the day is refused, so none of them move.
        TimingWorkflow.SessionShift shift = timings.shiftSession(day, null, 9999, WHO, true);

        assertFalse(shift.succeeded());
        assertFalse(shift.blockers().isEmpty());
        timings.papers().stream()
                .filter(row -> row.date().equals(day))
                .forEach(row -> assertFalse(row.overridden(),
                        "a half-moved day is worse than an unmoved one"));
    }

    @Test
    void anOverrideSurvivesARecomputeAndAReimport() {
        timings.save(new TimingOverride("E1", null, LocalTime.of(11, 0), null), WHO, false);

        workspace.setData(SampleData.load());

        assertEquals(LocalTime.of(11, 0), paper("E1").start(),
                "a saved timing is not part of the plan, so recomputing must not discard it");
        assertEquals(LocalTime.of(11, 0),
                workspace.result().plan().slot("E1").getStartTime());
    }
}
