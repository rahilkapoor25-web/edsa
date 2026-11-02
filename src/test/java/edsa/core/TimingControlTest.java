package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Re-timing a paper: what the mode still refuses, what an authorised person may set, and what
 * the review tells them before they save.
 */
class TimingControlTest {

    private final ExamPlanner planner = new ExamPlanner();
    private final TimingValidator validator =
            new TimingValidator(planner, SchedulingConfig.defaults());

    /** Two CSE papers sat by the same cohort, so moving one onto the other must clash. */
    private ExamData sharedCohort() {
        return new ExamData(
                TestData.students("S", 4, "CS301", "CS302"),
                TestData.rooms(4, 4),
                TestData.faculty(4),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "CS302", LocalTime.of(14, 0))));
    }

    @Test
    void theWorkbookStillCannotStretchAQuiz() {
        Exam declared = TestData.exam("E1", "CS301", LocalTime.of(9, 0), 90);

        assertEquals(10, declared.durationUnder(ExamMode.QUIZ),
                "a declared length is still ignored: that is the bug this must not reopen");
        assertTrue(declared.overrideIgnoredUnder(ExamMode.QUIZ));
        assertFalse(declared.hasApprovedTiming());
    }

    @Test
    void anAuthorisedLengthIsHonouredWhereTheWorkbookWasNot() {
        Exam declared = TestData.exam("E1", "CS301", LocalTime.of(9, 0), 90);
        Exam retimed = declared.retimed(null, null, 45);

        assertEquals(10, declared.durationUnder(ExamMode.QUIZ));
        assertEquals(45, retimed.durationUnder(ExamMode.QUIZ),
                "a length somebody signed for is a decision, not a stale cell");
        assertTrue(retimed.hasApprovedTiming());
    }

    @Test
    void theModeStillBoundsWhatAnAuthorisedPersonMaySet() {
        assertFalse(ExamMode.ENDSEM.permitsApproved(301));
        assertFalse(ExamMode.ENDSEM.permitsApproved(4));
        assertFalse(ExamMode.ENDSEM.permitsApproved(null));
        assertTrue(ExamMode.ENDSEM.permitsApproved(45));

        // Out of bounds falls back to the mode's own length rather than producing nonsense.
        assertEquals(180, ExamMode.ENDSEM.minutesForApproved(301));
    }

    @Test
    void retimingOnePartLeavesTheOthersAlone() {
        Exam declared = new Exam("E1", "CS301", "CS301 paper",
                LocalDate.of(2026, 11, 10), LocalTime.of(9, 0), null, 120);

        Exam movedOnly = declared.retimed(null, LocalTime.of(14, 0), null);

        assertEquals(LocalDate.of(2026, 11, 10), movedOnly.getDate(), "the date was not restated");
        assertEquals(LocalTime.of(14, 0), movedOnly.getPermittedStart().orElseThrow());
        assertEquals(120, movedOnly.getApprovedDurationMinutes().orElseThrow(),
                "a change of start must not quietly clear an approved length");
    }

    @Test
    void theDurationRuleChecksAnApprovedLengthToo() {
        ExamData data = new ExamData(
                TestData.students("S", 2, "CS301"),
                TestData.rooms(1, 2),
                TestData.faculty(1),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)).retimed(null, null, 45)));

        PlanningResult result = planner.plan(data, ExamMode.ENDSEM);

        assertEquals(45, result.plan().slot("E1").getDurationMinutes());
        CheckResult duration = result.checks().stream()
                .filter(check -> check.constraintName().equals("Exam duration"))
                .findFirst().orElseThrow();
        assertTrue(duration.satisfied(),
                "a sitting timed to what was approved for it must not be reported as a breach: "
                        + duration.breaches());
    }

    @Test
    void overridesReachTheWholePlanThroughTheDataAndNotPieceByPiece() {
        ExamData data = sharedCohort();
        TimingOverrides overrides = TimingOverrides.none()
                .with(new TimingOverride("E2", null, LocalTime.of(16, 0), null));

        ExamData retimed = overrides.applyTo(data);

        assertEquals(LocalTime.of(16, 0), retimed.exam("E2").getPermittedStart().orElseThrow());
        assertEquals(LocalTime.of(9, 0), retimed.exam("E1").getPermittedStart().orElseThrow(),
                "applying one override must not disturb another paper");
        assertEquals(LocalTime.of(14, 0), data.exam("E2").getPermittedStart().orElseThrow(),
                "the data handed in is never edited, so a review cannot leak into the live plan");
    }

    @Test
    void aLengthNoModeAcceptsIsBlockedNotWarned() {
        TimingReview review = validator.review(sharedCohort(), ExamMode.ENDSEM,
                TimingOverrides.none(), new TimingOverride("E1", null, null, 999), null);

        assertTrue(review.isBlocked());
        assertTrue(review.blockers().get(0).contains("between 5 and 300 minutes"));
    }

    @Test
    void aSittingRunningPastClosingIsBlocked() {
        TimingReview review = validator.review(sharedCohort(), ExamMode.ENDSEM,
                TimingOverrides.none(), new TimingOverride("E1", null, LocalTime.of(17, 0), null), null);

        assertTrue(review.isBlocked());
        assertTrue(review.blockers().get(0).contains("after the hall closes"),
                review.blockers().toString());
    }

    @Test
    void aPaperThatDoesNotExistIsBlocked() {
        TimingReview review = validator.review(sharedCohort(), ExamMode.ENDSEM,
                TimingOverrides.none(), new TimingOverride("NOPE", null, LocalTime.of(10, 0), null), null);

        assertTrue(review.isBlocked());
        assertTrue(review.blockers().get(0).contains("no paper with id NOPE"));
    }

    @Test
    void aChangeThatChangesNothingIsBlocked() {
        TimingReview review = validator.review(sharedCohort(), ExamMode.ENDSEM,
                TimingOverrides.none(), new TimingOverride("E1", null, null, null), null);

        assertTrue(review.isBlocked());
        assertEquals("Nothing was changed", review.blockers().get(0));
    }

    @Test
    void aStudentClashIsAWarningTheOfficeMayAccept() {
        ExamData data = sharedCohort();
        PlanningResult current = planner.plan(data, ExamMode.MIDSEM);

        TimingReview review = validator.review(data, ExamMode.MIDSEM, TimingOverrides.none(),
                new TimingOverride("E2", null, LocalTime.of(9, 0), null), current);

        assertFalse(review.isBlocked(), "a clash is the office's call, not an impossibility");
        assertTrue(review.hasWarnings());
        assertTrue(review.warnings().stream().anyMatch(w -> w.contains("already sitting")),
                review.warnings().toString());
    }

    @Test
    void aCleanMoveWarnsAboutNothing() {
        ExamData data = sharedCohort();
        PlanningResult current = planner.plan(data, ExamMode.MIDSEM);

        TimingReview review = validator.review(data, ExamMode.MIDSEM, TimingOverrides.none(),
                new TimingOverride("E2", null, LocalTime.of(15, 0), null), current);

        assertTrue(review.isClean(), "moving a paper an hour later breaks nothing: "
                + review.findings());
    }

    /**
     * A session with a problem of its own keeps it whatever else changes. Reporting it as though
     * the coordinator had just caused it would train them to ignore the warnings.
     */
    @Test
    void aProblemThatWasAlreadyThereIsNotBlamedOnTheChange() {
        ExamData tooFewRooms = new ExamData(
                TestData.students("S", 20, "CS301"),
                TestData.rooms(1, 2),
                TestData.faculty(2),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "CS301", LocalTime.of(14, 0))));
        PlanningResult current = planner.plan(tooFewRooms, ExamMode.MIDSEM);
        assertFalse(current.problems().isEmpty(), "this session is meant to start out broken");

        TimingReview review = validator.review(tooFewRooms, ExamMode.MIDSEM, TimingOverrides.none(),
                new TimingOverride("E2", null, LocalTime.of(15, 0), null), current);

        assertTrue(review.warnings().isEmpty(),
                "only what the change adds should be shown, not the session's existing problems: "
                        + review.warnings());
    }

    @Test
    void anOverrideCanBeTakenBackLeavingTheDeclaredTiming() {
        TimingOverrides overrides = TimingOverrides.none()
                .with(new TimingOverride("E2", null, LocalTime.of(16, 0), null));

        assertEquals(1, overrides.size());
        assertTrue(overrides.without("E2").isEmpty());
        assertEquals(LocalTime.of(16, 0),
                overrides.applyTo(sharedCohort()).exam("E2").getPermittedStart().orElseThrow());
        assertEquals(LocalTime.of(14, 0),
                overrides.without("E2").applyTo(sharedCohort()).exam("E2").getPermittedStart().orElseThrow());
    }
}
