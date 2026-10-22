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
        assertEquals(List.of(120, 120), midsem.stream().sorted(java.util.Comparator.comparing(ExamSlot::getId))
                .map(ExamSlot::getDurationMinutes).toList(),
                "both papers take the mode's length, declared value or not");
        assertEquals(10, schedule(data, ExamMode.QUIZ).get(0).getDurationMinutes(),
                "going back to Quiz must re-time it, not keep the Midsem length");
    }

    // ── Overrides the longer modes do accept ──────────────────────────────────────────────

    @Test
    void midsemAndEndsemIgnoreADeclaredLengthToo() {
        ExamData data = data(List.of(paper("E1", "CS301", 90)));

        assertEquals(120, schedule(data, ExamMode.MIDSEM).get(0).getDurationMinutes());
        assertEquals(180, schedule(data, ExamMode.ENDSEM).get(0).getDurationMinutes());
        assertEquals(LocalTime.of(12, 0), schedule(data, ExamMode.ENDSEM).get(0).getEndTime(),
                "a ninety-minute value in the workbook must not shorten an Endsem paper");
    }

    @Test
    void aDeclaredLengthBeyondTheMaximumFallsBackToTheMode() {
        List<ExamSlot> slots = schedule(data(List.of(paper("E1", "CS301",
                ExamMode.MAX_OVERRIDE_MINUTES + 1))), ExamMode.ENDSEM);

        assertEquals(180, slots.get(0).getDurationMinutes());
    }

    /**
     * The reported bug, exactly as it appeared: a timetable headed "Endsem · 3 h" with
     * Database Systems sitting in it at 09:00-10:30 because the workbook carried 90 for that
     * one paper. Every paper in an Endsem session runs the full three hours.
     */
    @Test
    void everyEndsemPaperRunsForThreeHoursWhicheverCarriesADeclaredLength() {
        List<Exam> exams = List.of(
                paper("E1", "CS301", null),
                paper("E2", "CS302", null),
                paper("E3", "CS303", 90));

        List<ExamSlot> slots = schedule(data(exams), ExamMode.ENDSEM);

        assertEquals(3, slots.size());
        for (ExamSlot slot : slots) {
            assertEquals(180, slot.getDurationMinutes(), slot.getPaperCode() + " must run three hours");
            assertEquals("3 h", slot.getDurationLabel(), slot.getPaperCode());
            assertEquals(LocalTime.of(9, 0), slot.getStartTime());
            assertEquals(LocalTime.of(12, 0), slot.getEndTime(), slot.getPaperCode());
        }
    }

    @Test
    void theThreeModesKeepTheirOwnDistinctLengths() {
        ExamData data = data(List.of(paper("E1", "CS301", 90)));

        assertEquals(10, schedule(data, ExamMode.QUIZ).get(0).getDurationMinutes());
        assertEquals(120, schedule(data, ExamMode.MIDSEM).get(0).getDurationMinutes());
        assertEquals(180, schedule(data, ExamMode.ENDSEM).get(0).getDurationMinutes());
    }

    @Test
    void noModeAcceptsADeclaredLength() {
        assertFalse(ExamMode.QUIZ.allowsDurationOverride());
        assertFalse(ExamMode.MIDSEM.allowsDurationOverride());
        assertFalse(ExamMode.ENDSEM.allowsDurationOverride());

        for (ExamMode mode : ExamMode.values()) {
            assertEquals(mode.defaultMinutes(), mode.minutesFor(90), mode + " with a declared 90");
            assertEquals(mode.defaultMinutes(), mode.minutesFor(null), mode + " with nothing declared");
            assertEquals(mode.defaultMinutes(), mode.minutesFor(0), mode + " with a nonsense length");
        }
    }

    @Test
    void anIgnoredValueIsReportedRatherThanAppliedSilently() {
        ExamData data = data(List.of(paper("E1", "CS301", 90), paper("E2", "CS302", null)));

        List<String> ignored = data.ignoredDurationOverrides(ExamMode.QUIZ);

        assertEquals(1, ignored.size());
        assertTrue(ignored.get(0).contains("CS301"), ignored.get(0));
        assertTrue(ignored.get(0).contains("90 minutes"), ignored.get(0));
        assertTrue(ignored.get(0).contains("10 minutes"), ignored.get(0));

        // Every mode now reports it, because no mode acts on it.
        assertEquals(1, data.ignoredDurationOverrides(ExamMode.MIDSEM).size());
        assertEquals(1, data.ignoredDurationOverrides(ExamMode.ENDSEM).size());
        assertTrue(data.ignoredDurationOverrides(ExamMode.ENDSEM).get(0).contains("180 minutes"));
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
