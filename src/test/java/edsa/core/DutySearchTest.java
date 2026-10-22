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
 * Searching for an invigilator by name, and getting the rooms they are actually in.
 *
 * <p>The scenario has two colleagues who share a first name on purpose, because telling
 * "Aman Verma" from "Aman Gupta" is the case that goes wrong if a search is sloppy.
 */
class DutySearchTest {

    private final ExamPlanner planner = new ExamPlanner();

    private static Faculty member(String id, String name, String department, boolean senior) {
        return new Faculty(id, name, department, senior, Set.of());
    }

    /**
     * Three papers at the same time in three rooms, so there is a duty each for three
     * invigilators, and the two who share a name are both busy.
     */
    private ExamPlan threeParallelPapers() {
        List<Student> students = new ArrayList<>();
        for (String code : List.of("CS301", "CS302", "CS303")) {
            for (int index = 1; index <= 4; index++) {
                students.add(new Student(code + "-S" + index, "Student " + index, "CSE", Set.of(code)));
            }
        }
        ExamData data = new ExamData(
                students,
                TestData.rooms(3, 10),
                List.of(member("F01", "Dr. Aman Verma", "CSE", true),
                        member("F02", "Dr. Aman Gupta", "ECE", false),
                        member("F03", "Prof. Sneha Rao", "ME", false),
                        member("F04", "Dr. Idle Nobody", "CSE", false)),
                List.of(new Exam("E1", "CS301", "Data Structures", TestData.DAY, LocalTime.of(9, 0), null),
                        new Exam("E2", "CS302", "Operating Systems", TestData.DAY, LocalTime.of(9, 0), null),
                        new Exam("E3", "CS303", "Databases", TestData.DAY.plusDays(1),
                                LocalTime.of(9, 0), null)));
        return planner.plan(data, ExamMode.MIDSEM).plan();
    }

    private static List<String> namesOf(List<DutySearchResult> results) {
        return results.stream().map(result -> result.member().getName()).toList();
    }

    // ── Matching ─────────────────────────────────────────────────────────────────────────

    @Test
    void aFullNameFindsExactlyThatPerson() {
        List<DutySearchResult> results = threeParallelPapers().invigilatorsNamed("Dr. Aman Verma");

        assertEquals(List.of("Dr. Aman Verma"), namesOf(results));
    }

    @Test
    void partOfANameIsEnough() {
        List<DutySearchResult> results = threeParallelPapers().invigilatorsNamed("Sneha");

        assertEquals(List.of("Prof. Sneha Rao"), namesOf(results));
    }

    @Test
    void aSurnameFragmentMatchesToo() {
        assertEquals(List.of("Dr. Aman Verma"), namesOf(threeParallelPapers().invigilatorsNamed("verm")));
    }

    @Test
    void capitalsDoNotMatter() {
        ExamPlan plan = threeParallelPapers();

        assertEquals(namesOf(plan.invigilatorsNamed("SNEHA")), namesOf(plan.invigilatorsNamed("sneha")));
        assertEquals(List.of("Prof. Sneha Rao"), namesOf(plan.invigilatorsNamed("sNeHa")));
    }

    @Test
    void surroundingSpacesAreIgnored() {
        assertEquals(List.of("Prof. Sneha Rao"),
                namesOf(threeParallelPapers().invigilatorsNamed("   Sneha   ")));
    }

    @Test
    void nothingMatchesAnUnknownName() {
        assertTrue(threeParallelPapers().invigilatorsNamed("Nobody At All").isEmpty());
    }

    @Test
    void aBlankSearchMatchesNobodyRatherThanEverybody() {
        ExamPlan plan = threeParallelPapers();

        assertTrue(plan.invigilatorsNamed("").isEmpty(), "clearing the box must restore the page");
        assertTrue(plan.invigilatorsNamed("   ").isEmpty());
        assertTrue(plan.invigilatorsNamed(null).isEmpty());
    }

    // ── Two people with similar names ────────────────────────────────────────────────────

    @Test
    void twoColleaguesSharingANameComeBackSeparately() {
        List<DutySearchResult> results = threeParallelPapers().invigilatorsNamed("Aman");

        assertEquals(2, results.size(), "both should be found, not merged into one");
        assertEquals(List.of("Dr. Aman Gupta", "Dr. Aman Verma"), namesOf(results));
    }

    @Test
    void theyCanBeToldApartByIdAndDepartment() {
        List<DutySearchResult> results = threeParallelPapers().invigilatorsNamed("Aman");

        DutySearchResult gupta = results.get(0);
        DutySearchResult verma = results.get(1);
        assertEquals("F02", gupta.member().getId());
        assertEquals("ECE", gupta.member().getDepartment());
        assertEquals("F01", verma.member().getId());
        assertEquals("CSE", verma.member().getDepartment());
    }

    @Test
    void eachOfThemGetsOnlyTheirOwnDuties() {
        List<DutySearchResult> results = threeParallelPapers().invigilatorsNamed("Aman");

        for (DutySearchResult result : results) {
            for (FacultyDuty duty : result.duties()) {
                assertEquals(result.member().getId(), duty.member().getId(),
                        "a duty must never be listed under the wrong person");
            }
        }
    }

    // ── The duties themselves ────────────────────────────────────────────────────────────

    @Test
    void everyRoomShownComesFromAnActualDutyInTheRoster() {
        ExamPlan plan = threeParallelPapers();

        for (DutySearchResult result : plan.invigilatorsNamed("Dr.")) {
            for (FacultyDuty duty : result.duties()) {
                boolean real = plan.duties().all().stream().anyMatch(assignment ->
                        assignment.facultyId().equals(result.member().getId())
                                && assignment.slotId().equals(duty.slot().getId())
                                && assignment.roomId().equals(duty.room().getId()));
                assertTrue(real, "room " + duty.room().getId() + " is not in the roster for "
                        + result.member().getId());
            }
        }
    }

    @Test
    void aProfessorWithDutiesInSeveralRoomsAndSessionsGetsAllOfThem() {
        ExamPlan plan = threeParallelPapers();

        // Whoever ended up with more than one duty should have every one of them listed.
        DutySearchResult busiest = plan.invigilatorsNamed("Dr.").stream()
                .max((a, b) -> Integer.compare(a.dutyCount(), b.dutyCount()))
                .orElseThrow();
        long fromRoster = plan.duties().forFaculty(busiest.member().getId()).size();

        assertEquals(fromRoster, busiest.dutyCount(), "every duty in the roster should be listed");
        assertTrue(busiest.dutyCount() >= 1);
    }

    @Test
    void dutiesComeBackEarliestFirst() {
        ExamPlan plan = threeParallelPapers();

        for (DutySearchResult result : plan.invigilatorsNamed("Dr.")) {
            List<FacultyDuty> duties = result.duties();
            for (int index = 1; index < duties.size(); index++) {
                var earlier = duties.get(index - 1).slot();
                var later = duties.get(index).slot();
                assertTrue(earlier.getDate().isBefore(later.getDate())
                                || (earlier.getDate().equals(later.getDate())
                                && !earlier.getStartTime().isAfter(later.getStartTime())),
                        "duties should read in time order");
            }
        }
    }

    @Test
    void eachDutyCarriesTheDetailsAPageNeeds() {
        DutySearchResult result = threeParallelPapers().invigilatorsNamed("Aman Verma").get(0);
        FacultyDuty duty = result.duties().get(0);

        assertEquals(TestData.DAY, duty.slot().getDate());
        assertEquals(LocalTime.of(9, 0), duty.slot().getStartTime());
        assertFalse(duty.slot().getPaperCode().isBlank());
        assertFalse(duty.slot().getPaperName().isBlank());
        assertFalse(duty.room().getId().isBlank());
        assertFalse(duty.room().getBuilding().isBlank());
        assertTrue(duty.seatedInRoom() > 0, "the page shows how many are in the room");
    }

    @Test
    void theRoleReflectsSeniorityRatherThanBeingInvented() {
        List<DutySearchResult> results = threeParallelPapers().invigilatorsNamed("Aman");

        assertEquals("Invigilator", results.get(0).duties().isEmpty()
                ? "Invigilator" : results.get(0).duties().get(0).role());
        DutySearchResult senior = results.get(1);
        assertTrue(senior.member().isSenior());
        assertEquals("Senior invigilator", senior.duties().get(0).role());
    }

    // ── Somebody with nothing to do ──────────────────────────────────────────────────────

    @Test
    void aMatchedProfessorWithNoDutiesIsStillReported() {
        List<DutySearchResult> results = threeParallelPapers().invigilatorsNamed("Idle");

        assertEquals(1, results.size(), "being found with nothing to do is not the same as not being found");
        assertFalse(results.get(0).hasDuties());
        assertEquals(0, results.get(0).dutyCount());
        assertEquals("none", results.get(0).roomSummary());
    }

    // ── The search follows the current allocation ────────────────────────────────────────

    @Test
    void theResultsFollowTheCurrentAllocationRatherThanAnOldOne() {
        ExamData data = new ExamData(
                TestData.students("S", 4, "CS301"),
                TestData.rooms(1, 10),
                List.of(member("F01", "Dr. Aman Verma", "CSE", true),
                        member("F02", "Dr. Aman Gupta", "ECE", false)),
                List.of(new Exam("E1", "CS301", "Data Structures", TestData.DAY, LocalTime.of(9, 0), null)));

        ExamPlan before = planner.plan(data, ExamMode.MIDSEM).plan();
        String onDuty = before.duties().all().get(0).facultyId();

        // Cover that person's absence; the duty moves to their colleague.
        RepairResult repair = new AbsenceRepairer().repair(before, onDuty, AbsenceWindow.wholeSession());
        ExamPlan after = repair.plan();

        DutySearchResult wasOnDuty = after.invigilatorsNamed("Aman").stream()
                .filter(result -> result.member().getId().equals(onDuty))
                .findFirst().orElseThrow();
        assertFalse(wasOnDuty.hasDuties(), "the search must not keep showing a duty they no longer hold");

        DutySearchResult tookOver = after.invigilatorsNamed("Aman").stream()
                .filter(result -> !result.member().getId().equals(onDuty))
                .findFirst().orElseThrow();
        assertTrue(tookOver.hasDuties(), "and it must show the duty where it went");
    }

    @Test
    void changingTheModeChangesTheTimesTheSearchReports() {
        ExamData data = new ExamData(
                TestData.students("S", 4, "CS301"),
                TestData.rooms(1, 10),
                List.of(member("F01", "Dr. Aman Verma", "CSE", true)),
                List.of(new Exam("E1", "CS301", "Data Structures", TestData.DAY, LocalTime.of(9, 0), null)));

        FacultyDuty asMidsem = planner.plan(data, ExamMode.MIDSEM).plan()
                .invigilatorsNamed("Aman").get(0).duties().get(0);
        FacultyDuty asQuiz = planner.plan(data, ExamMode.QUIZ).plan()
                .invigilatorsNamed("Aman").get(0).duties().get(0);

        assertEquals(LocalTime.of(11, 0), asMidsem.slot().getEndTime());
        assertEquals(LocalTime.of(9, 10), asQuiz.slot().getEndTime());
    }
}
