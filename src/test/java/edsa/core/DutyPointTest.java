package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** -1 for each duty a confirmed absence makes somebody miss, +1 for each duty somebody covers. */
class DutyPointTest {

    private final AbsenceRepairer repairer = new AbsenceRepairer();

    /** CS301 at 09:00 and EC301 at 14:00, one room each: F1 takes the first, F2 the second. */
    private static ExamPlan plan(int invigilators) {
        ExamData data = new ExamData(
                Stream.concat(TestData.students("S", 5, "CS301").stream(),
                        TestData.students("T", 5, "EC301").stream()).toList(),
                TestData.rooms(2, 10),
                TestData.faculty(invigilators),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(14, 0))));
        return new ExamPlanner().plan(data, ExamMode.MIDSEM).plan();
    }

    private List<DutyPoint> pointsFor(ExamPlan plan, String absent, AbsenceWindow window) {
        return DutyPoint.earnedBy(plan, window, repairer.repair(plan, absent, window));
    }

    @Test
    void theAbsentTeacherLosesAPointAndTheCoverGainsOne() {
        List<DutyPoint> points = pointsFor(plan(3), "F1", AbsenceWindow.wholeSession());

        assertEquals(List.of("F1:-1", "F3:1"),
                points.stream().map(point -> point.facultyId() + ":" + point.points()).toList());
        assertTrue(points.get(0).reason().contains("CS301"), points.get(0).reason());
        assertTrue(points.get(1).reason().contains("Faculty 1"), points.get(1).reason());
    }

    @Test
    void anAbsenceThatMissesNoDutyEarnsNothing() {
        // F1's only duty is the morning paper; away in the afternoon misses nothing.
        AbsenceWindow afternoon = AbsenceWindow.hours(TestData.DAY, LocalTime.of(13, 0), LocalTime.of(18, 0));

        assertEquals(List.of(), pointsFor(plan(3), "F1", afternoon));
    }

    @Test
    void aDutyNobodyCouldCoverStillCostsTheAbsentTeacherAPoint() {
        // F1 is the only Professor, so nobody may take either of their rooms.
        List<DutyPoint> points = pointsFor(plan(2), "F1", AbsenceWindow.wholeSession());

        assertEquals(List.of("F1:-1", "F1:-1"),
                points.stream().map(point -> point.facultyId() + ":" + point.points()).toList());
    }
}
