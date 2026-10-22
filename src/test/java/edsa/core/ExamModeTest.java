package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The three modes, and what they do to a timetable. */
class ExamModeTest {

    private final ExamPlanner planner = new ExamPlanner();

    private static ExamData oneExam() {
        return new ExamData(TestData.students("S", 4, "CS301"), TestData.rooms(1, 10),
                TestData.faculty(2), List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));
    }

    @Test
    void quizRunsForTenMinutes() {
        assertEquals(10, ExamMode.QUIZ.defaultMinutes());
    }

    @Test
    void midsemRunsForTwoHours() {
        assertEquals(120, ExamMode.MIDSEM.defaultMinutes());
    }

    @Test
    void endsemRunsForThreeHours() {
        assertEquals(180, ExamMode.ENDSEM.defaultMinutes());
    }

    @Test
    void quizEndsTenMinutesAfterItStarts() {
        ExamSlot slot = planner.plan(oneExam(), ExamMode.QUIZ).plan().schedule().get(0);

        assertEquals(LocalTime.of(9, 0), slot.getStartTime());
        assertEquals(LocalTime.of(9, 10), slot.getEndTime());
        assertEquals(10, slot.getDurationMinutes());
    }

    @Test
    void midsemEndsTwoHoursAfterItStarts() {
        ExamSlot slot = planner.plan(oneExam(), ExamMode.MIDSEM).plan().schedule().get(0);

        assertEquals(LocalTime.of(11, 0), slot.getEndTime());
        assertEquals(120, slot.getDurationMinutes());
    }

    @Test
    void changingTheModeRetimesTheWholeTimetable() {
        ExamData data = oneExam();

        ExamSlot asQuiz = planner.plan(data, ExamMode.QUIZ).plan().schedule().get(0);
        ExamSlot asEndsem = planner.plan(data, ExamMode.ENDSEM).plan().schedule().get(0);

        assertEquals(LocalTime.of(9, 10), asQuiz.getEndTime());
        assertEquals(LocalTime.of(12, 0), asEndsem.getEndTime());
    }

    @Test
    void aPaperCannotOverrideTheModesDuration() {
        ExamData data = new ExamData(TestData.students("S", 4, "CS301"), TestData.rooms(1, 10),
                TestData.faculty(2), List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0), 45)));

        ExamSlot slot = planner.plan(data, ExamMode.ENDSEM).plan().schedule().get(0);

        assertEquals(180, slot.getDurationMinutes(), "an Endsem paper runs for three hours");
        assertEquals(LocalTime.of(12, 0), slot.getEndTime());
    }

    @Test
    void refusesAnUnknownModeName() {
        assertThrows(IllegalArgumentException.class, () -> ExamMode.of("FINALS"));
    }

    @Test
    void readsModeNamesCaseInsensitively() {
        assertEquals(ExamMode.MIDSEM, ExamMode.of("midsem"));
        assertEquals(ExamMode.QUIZ, ExamMode.of("Quiz"));
    }
}
