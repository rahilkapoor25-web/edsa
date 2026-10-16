package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The duration pipeline, end to end: the selected mode decides how long every sitting runs,
 * and a {@code Duration Minutes} value left in a workbook can never stretch a Quiz.
 *
 * <p>These are the regression tests for the bug where one paper — reliably the last row of the
 * Exams worksheet, because that is where the stale value sat — came out at 90 minutes in Quiz
 * mode. Each case asserts every generated slot independently, so a fix that only works for the
 * paper that happened to be affected would still fail here.
 */
class ExamDurationTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);

    private final ExamPlanner planner = new ExamPlanner();

    private static Exam paper(String id, String code, Integer declaredMinutes) {
        return new Exam(id, code, code + " paper", TestData.DAY, NINE, declaredMinutes);
    }

    /** One cohort per paper, so every paper can run in parallel and times stay comparable. */
    private static ExamData data(List<Exam> exams) {
        List<Student> students = new ArrayList<>();
        for (Exam exam : exams) {
            for (int index = 1; index <= 3; index++) {
                students.add(new Student(exam.getSubjectCode() + "-S" + index,
                        "Student " + index, "CSE", Set.of(exam.getSubjectCode())));
            }
        }
        return new ExamData(students, TestData.rooms(4, 10), TestData.faculty(6), exams);
    }

    private List<ExamSlot> schedule(ExamData data, ExamMode mode) {
        PlanningResult result = planner.plan(data, mode);
        assertTrue(result.isUsable(), "the plan should be usable: " + result.problems());
        return result.plan().schedule();
    }

    // ── The standard durations ────────────────────────────────────────────────────────────

    @Test
    void theStandardDurationsAreTenOneHundredAndTwentyAndOneHundredAndEighty() {
        assertEquals(10, ExamMode.QUIZ.defaultMinutes());
        assertEquals(120, ExamMode.MIDSEM.defaultMinutes());
        assertEquals(180, ExamMode.ENDSEM.defaultMinutes());
    }

    @Test
    void quizIsExactlyTenMinutesAndEndsTenMinutesLater() {
        List<ExamSlot> slots = schedule(data(List.of(paper("E1", "CS301", null))), ExamMode.QUIZ);

        assertEquals(10, slots.get(0).getDurationMinutes());
        assertEquals(NINE, slots.get(0).getStartTime());
        assertEquals(LocalTime.of(9, 10), slots.get(0).getEndTime());
    }

    @Test
    void midsemIsExactlyOneHundredAndTwentyMinutes() {
        List<ExamSlot> slots = schedule(data(List.of(paper("E1", "CS301", null))), ExamMode.MIDSEM);

        assertEquals(120, slots.get(0).getDurationMinutes());
        assertEquals(LocalTime.of(11, 0), slots.get(0).getEndTime());
    }

    @Test
    void endsemIsExactlyOneHundredAndEightyMinutes() {
        List<ExamSlot> slots = schedule(data(List.of(paper("E1", "CS301", null))), ExamMode.ENDSEM);

        assertEquals(180, slots.get(0).getDurationMinutes());
        assertEquals(LocalTime.of(12, 0), slots.get(0).getEndTime());
    }

    // ── The bug ───────────────────────────────────────────────────────────────────────────

    @Test
    void aQuizIgnoresANinetyMinuteValueLeftInTheWorkbook() {
        List<ExamSlot> slots = schedule(data(List.of(paper("E1", "CS301", 90))), ExamMode.QUIZ);

        assertEquals(10, slots.get(0).getDurationMinutes(), "a Quiz is ten minutes, whatever the workbook says");
        assertEquals(LocalTime.of(9, 10), slots.get(0).getEndTime());
    }

    @Test
    void theLastPaperIsTimedLikeEveryOtherPaper() {
        List<Exam> exams = List.of(
                paper("E1", "CS301", null),
                paper("E2", "CS302", null),
                paper("E3", "CS303", null),
                paper("E4", "CS304", 90));

        List<ExamSlot> slots = schedule(data(exams), ExamMode.QUIZ);

        assertEquals(4, slots.size());
        slots.forEach(slot -> assertEquals(10, slot.getDurationMinutes(),
                slot.getPaperCode() + " should be a ten-minute Quiz"));
    }

    @Test
    void everyQuizSlotIsExactlyTenMinutesWhicheverRowCarriesTheValue() {
        for (int carrier = 0; carrier < 4; carrier++) {
            List<Exam> exams = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                exams.add(paper("E" + (index + 1), "CS30" + (index + 1), index == carrier ? 90 : null));
            }

            List<ExamSlot> slots = schedule(data(exams), ExamMode.QUIZ);

            assertEquals(4, slots.size());
            for (ExamSlot slot : slots) {
                assertEquals(10, slot.getDurationMinutes(),
                        slot.getPaperCode() + " with the value on row " + (carrier + 1));
                assertEquals(LocalTime.of(9, 10), slot.getEndTime());
            }
        }
    }

    @Test
    void rowOrderDoesNotChangeAnyDuration() {
        List<Exam> forwards = List.of(paper("E1", "CS301", 90), paper("E2", "CS302", null),
                paper("E3", "CS303", 45));
        List<Exam> backwards = List.of(paper("E3", "CS303", 45), paper("E2", "CS302", null),
                paper("E1", "CS301", 90));

        schedule(data(forwards), ExamMode.QUIZ)
                .forEach(slot -> assertEquals(10, slot.getDurationMinutes(), slot.getPaperCode()));
        schedule(data(backwards), ExamMode.QUIZ)
                .forEach(slot -> assertEquals(10, slot.getDurationMinutes(), slot.getPaperCode()));
    }

    @Test
    void aBlankDurationCellUsesTheSelectedMode() {
        ExamData data = data(List.of(paper("E1", "CS301", null)));

        assertEquals(10, schedule(data, ExamMode.QUIZ).get(0).getDurationMinutes());
        assertEquals(120, schedule(data, ExamMode.MIDSEM).get(0).getDurationMinutes());
        assertEquals(180, schedule(data, ExamMode.ENDSEM).get(0).getDurationMinutes());
    }

    @Test
    void changingBetweenAllThreeModesRetimesEveryPaper() {
        ExamData data = data(List.of(paper("E1", "CS301", 90), paper("E2", "CS302", null)));

        schedule(data, ExamMode.QUIZ).forEach(slot -> {
            assertEquals(10, slot.getDurationMinutes());
            assertEquals(LocalTime.of(9, 10), slot.getEndTime());
        });
        List<ExamSlot> midsem = schedule(data, ExamMode.MIDSEM);
        assertEquals(List.of(90, 120), midsem.stream().sorted(java.util.Comparator.comparing(ExamSlot::getId))
                .map(ExamSlot::getDurationMinutes).toList());
        assertEquals(10, schedule(data, ExamMode.QUIZ).get(0).getDurationMinutes(),
                "going back to Quiz must re-time it, not keep the Midsem length");
    }

    // ── Overrides the longer modes do accept ──────────────────────────────────────────────

    @Test
    void midsemAndEndsemStillHonourADeclaredLength() {
        ExamData data = data(List.of(paper("E1", "CS301", 90)));

        assertEquals(90, schedule(data, ExamMode.MIDSEM).get(0).getDurationMinutes());
        assertEquals(90, schedule(data, ExamMode.ENDSEM).get(0).getDurationMinutes());
        assertEquals(LocalTime.of(10, 30), schedule(data, ExamMode.ENDSEM).get(0).getEndTime());
    }

    @Test
    void aDeclaredLengthBeyondTheMaximumFallsBackToTheMode() {
        List<ExamSlot> slots = schedule(data(List.of(paper("E1", "CS301",
                ExamMode.MAX_OVERRIDE_MINUTES + 1))), ExamMode.ENDSEM);

        assertEquals(180, slots.get(0).getDurationMinutes());
    }

    @Test
    void theModeReportsWhetherItAcceptsADeclaredLength() {
        assertFalse(ExamMode.QUIZ.allowsDurationOverride());
        assertTrue(ExamMode.MIDSEM.allowsDurationOverride());
        assertTrue(ExamMode.ENDSEM.allowsDurationOverride());

        assertEquals(10, ExamMode.QUIZ.minutesFor(90));
        assertEquals(10, ExamMode.QUIZ.minutesFor(null));
        assertEquals(90, ExamMode.MIDSEM.minutesFor(90));
        assertEquals(120, ExamMode.MIDSEM.minutesFor(null));
        assertEquals(120, ExamMode.MIDSEM.minutesFor(0), "a nonsense length is not a preference");
    }

    @Test
    void anIgnoredValueIsReportedRatherThanAppliedSilently() {
        ExamData data = data(List.of(paper("E1", "CS301", 90), paper("E2", "CS302", null)));

        List<String> ignored = data.ignoredDurationOverrides(ExamMode.QUIZ);

        assertEquals(1, ignored.size());
        assertTrue(ignored.get(0).contains("CS301"), ignored.get(0));
        assertTrue(ignored.get(0).contains("90 minutes"), ignored.get(0));
        assertTrue(ignored.get(0).contains("10 minutes"), ignored.get(0));
        assertTrue(data.ignoredDurationOverrides(ExamMode.MIDSEM).isEmpty(),
                "Midsem accepts the declared length, so there is nothing to report");
    }

    // ── The independent check ─────────────────────────────────────────────────────────────

    @Test
    void theDurationRuleCatchesASittingTimedAgainstItsMode() {
        ExamData data = data(List.of(paper("E1", "CS301", 90)));
        ExamPlan tampered = new ExamPlan(data, ExamMode.QUIZ,
                List.of(new ExamSlot("E1", "CS301", "CS301 paper", TestData.DAY, NINE, 90)),
                new SeatingPlan(), new DutyRoster());

        List<String> breaches = new ExamDurationConstraint().breaches(tampered);

        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("must run for 10 minutes"), breaches.get(0));
    }

    @Test
    void theDurationRuleAcceptsAPlanTheSchedulerBuilt() {
        ExamData data = data(List.of(paper("E1", "CS301", 90), paper("E2", "CS302", null)));

        for (ExamMode mode : ExamMode.values()) {
            ExamPlan plan = planner.plan(data, mode).plan();
            assertTrue(new ExamDurationConstraint().isSatisfied(plan), mode + ": "
                    + new ExamDurationConstraint().breaches(plan));
        }
    }
}
