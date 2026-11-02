package edsa.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The guard on spending points: whether a duty may actually be given up.
 *
 * <p>This is the class that keeps the reward scheme from becoming a staffing problem, so the
 * cases that matter most here are the refusals — and above all the one where the points are
 * there, the policy allows it, and there is simply nobody free to stand in.
 */
class DutyReliefTest {

    private DutyEligibility eligibility(DutyPolicy policy) {
        return new DutyEligibility(SeniorityLadder.defaults(), policy, LeaveCalendar.empty());
    }

    private DutyRelief relief(DutyPolicy policy) {
        return new DutyRelief(eligibility(policy), RewardRules.defaults());
    }

    /** One paper in one room, with however many faculty are asked for. */
    private ExamPlan onePaper(int facultyCount) {
        ExamData data = new ExamData(
                TestData.students("S", 2, "CS301"),
                TestData.rooms(1, 2),
                TestData.faculty(facultyCount),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0))));
        return new ExamPlanner(SchedulingConfig.defaults(), eligibility(DutyPolicy.unweighted()))
                .plan(data, ExamMode.MIDSEM).plan();
    }

    /** Two papers at different times, so one person can hold two duties. */
    private ExamPlan twoPapersOnePerson(int facultyCount) {
        ExamData data = new ExamData(
                TestData.students("S", 2, "CS301", "CS302"),
                TestData.rooms(1, 2),
                TestData.faculty(facultyCount),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "CS302", LocalTime.of(14, 0))));
        return new ExamPlanner(SchedulingConfig.defaults(), eligibility(DutyPolicy.unweighted()))
                .plan(data, ExamMode.MIDSEM).plan();
    }

    private static PointsBalance rich() {
        return new PointsBalance("F1", 100, 0, 0, 0);
    }

    @Test
    void aDutySomebodyDoesNotHoldCannotBeGivenUp() {
        ExamPlan plan = onePaper(2);

        ReliefDecision decision = relief(DutyPolicy.unweighted())
                .check(plan, "F2", "E1", "R1", rich(), 0);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("does not belong to F2"), decision.reason());
    }

    @Test
    void aSittingOutsideThePlanIsRefused() {
        ReliefDecision decision = relief(DutyPolicy.unweighted())
                .check(onePaper(2), "F1", "NOPE", "R1", rich(), 0);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("not in the current plan"));
    }

    @Test
    void withoutEnoughPointsItIsRefusedWithTheFigures() {
        ExamPlan plan = onePaper(3);

        ReliefDecision decision = relief(DutyPolicy.unweighted())
                .check(plan, "F1", "E1", "R1", new PointsBalance("F1", 10, 0, 0, 0), 0);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("costs 30 points and only 10 are available"),
                decision.reason());
    }

    @Test
    void theMinimumNumberOfDutiesIsAFloorCreditsCannotBuyThrough() {
        DutyPolicy withFloor = new DutyPolicy(false, 0, 1, true, false);
        ExamPlan plan = onePaper(3);

        ReliefDecision decision = relief(withFloor).check(plan, "F1", "E1", "R1", rich(), 0);

        assertFalse(decision.allowed(), "however many points somebody has, the floor holds");
        assertTrue(decision.reason().contains("below the minimum of 1"), decision.reason());
    }

    @Test
    void aboveTheFloorItIsGrantedToANamedStandIn() {
        DutyPolicy withFloor = new DutyPolicy(false, 0, 1, true, false);
        ExamPlan plan = twoPapersOnePerson(2);

        // The allocator spreads duties, so it would never give both to one person. Moving them
        // by hand is the point: this test is about somebody who holds two duties and can afford
        // to lose one, which is the only situation in which a grant should ever be possible.
        for (Assignment duty : List.copyOf(plan.duties().all())) {
            plan.duties().reassign(duty.slotId(), duty.roomId(), "F1", DutyOrigin.MANUAL);
        }
        assertEquals(2, plan.duties().forFaculty("F1").size());
        assertEquals(0, plan.duties().forFaculty("F2").size());

        Assignment giveUp = plan.duties().forFaculty("F1").get(0);
        ReliefDecision decision = relief(withFloor)
                .check(plan, "F1", giveUp.slotId(), giveUp.roomId(), rich(), 0);

        assertTrue(decision.allowed(), decision.reason());
        assertEquals("F2", decision.replacementFacultyId(),
                "a grant must name who takes the room, not assume somebody will");
    }

    /**
     * The case the whole class exists for: the points are there, the policy allows it, and the
     * room would simply be left with nobody in it.
     */
    @Test
    void anExemptionIsRefusedWhenItWouldLeaveTheRoomUnstaffed() {
        ExamPlan plan = onePaper(1);
        assertEquals(1, plan.duties().all().size());

        ReliefDecision decision = relief(DutyPolicy.unweighted())
                .check(plan, "F1", "E1", "R1", rich(), 0);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("would be left unstaffed"), decision.reason());
    }

    @Test
    void theStandInIsNeverSomebodyAlreadyBusyAtThatHour() {
        ExamData data = new ExamData(
                java.util.stream.Stream.concat(
                        TestData.students("S", 2, "CS301").stream(),
                        TestData.students("T", 2, "EC301").stream()).toList(),
                TestData.rooms(2, 2),
                TestData.faculty(2),
                List.of(TestData.exam("E1", "CS301", LocalTime.of(9, 0)),
                        TestData.exam("E2", "EC301", LocalTime.of(9, 0))));
        ExamPlan plan = new ExamPlanner(SchedulingConfig.defaults(),
                eligibility(DutyPolicy.unweighted())).plan(data, ExamMode.MIDSEM).plan();

        // Both invigilators are busy at nine, so neither can cover for the other.
        ReliefDecision decision = relief(DutyPolicy.unweighted())
                .check(plan, "F1", "E1", "R1", rich(), 0);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("unstaffed"), decision.reason());
    }

    @Test
    void thePerSessionLimitIsEnforced() {
        ExamPlan plan = onePaper(3);

        ReliefDecision decision = relief(DutyPolicy.unweighted())
                .check(plan, "F1", "E1", "R1", rich(), 1);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("may be redeemed per session"), decision.reason());
    }

    @Test
    void withoutAPlanNothingCanBeGivenUp() {
        ReliefDecision decision = relief(DutyPolicy.unweighted())
                .check(null, "F1", "E1", "R1", rich(), 0);

        assertFalse(decision.allowed());
    }
}
