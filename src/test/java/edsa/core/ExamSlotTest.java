package edsa.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** Intervals are start-inclusive and end-exclusive, so touching sittings do not clash. */
class ExamSlotTest {

    private static ExamSlot at(String id, int hour, int minute, int minutes) {
        return new ExamSlot(id, "CS" + id, "paper", TestData.DAY, LocalTime.of(hour, minute), minutes);
    }

    @Test
    void aSittingInsideAnotherOverlaps() {
        assertTrue(at("1", 9, 0, 120).overlaps(at("2", 10, 0, 60)));
    }

    @Test
    void aSittingStartingAsAnotherEndsDoesNotOverlap() {
        ExamSlot morning = at("1", 9, 0, 120);
        ExamSlot next = at("2", 11, 0, 60);

        assertFalse(morning.overlaps(next));
        assertFalse(next.overlaps(morning));
    }

    @Test
    void aSittingStartingOneMinuteBeforeAnotherEndsOverlaps() {
        assertTrue(at("1", 9, 0, 120).overlaps(at("2", 10, 59, 60)));
    }

    @Test
    void sittingsOnDifferentDaysNeverOverlap() {
        ExamSlot today = at("1", 9, 0, 180);
        ExamSlot tomorrow = new ExamSlot("2", "CS302", "paper", TestData.DAY.plusDays(1),
                LocalTime.of(9, 0), 180);

        assertFalse(today.overlaps(tomorrow));
    }

    @Test
    void theEndTimeFollowsFromTheDuration() {
        ExamSlot slot = at("1", 9, 30, 150);

        assertTrue(slot.getEndTime().equals(LocalTime.of(12, 0)));
    }
}
