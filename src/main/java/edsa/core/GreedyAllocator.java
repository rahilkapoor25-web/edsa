package edsa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Seats the students and staffs the rooms for a timetable the scheduler has already placed.
 *
 * <p>A cohort is split across the rooms reserved for its sitting, filling each room's grid row
 * by row until the room is at capacity. Each used room then gets the least-loaded invigilator
 * who is free at that time, which keeps duties spread without needing a second pass.
 *
 * <p>Conflicts are tracked as the plan is built — which papers each student sits, which
 * sittings each invigilator covers — so placing a seat costs the same whether the session has
 * four rooms or forty. {@link PlanChecker} then proves the finished plan independently.
 *
 * <p>Who counts as least loaded depends on the {@link DutyPolicy} in force. Unweighted, it is
 * whoever has fewest duties, which is what this did before any ladder existed. Weighted, it is
 * whoever is furthest below the share their rank is expected to carry, so that asking less of
 * senior staff does not quietly mean asking twice as much of everybody else. Members who cannot
 * take a duty are passed over with a reason rather than silently skipped, because an unstaffed
 * room is the one outcome a coordinator must be able to explain.
 */
public final class GreedyAllocator {

    private final SchedulingConfig config;
    private final DutyEligibility eligibility;

    /**
     * Allocates under the default scheduling limits and the unweighted duty policy, which is
     * how this behaved before seniority existed. The pages pass a configured policy instead.
     */
    public GreedyAllocator(SchedulingConfig config) {
        this(config, new DutyEligibility(SeniorityLadder.defaults(), DutyPolicy.unweighted(),
                LeaveCalendar.empty()));
    }

    public GreedyAllocator(SchedulingConfig config, DutyEligibility eligibility) {
        this.config = config;
        this.eligibility = eligibility;
    }

    public GreedyAllocator() {
        this(SchedulingConfig.defaults());
    }

    public AllocationResult allocate(ExamData data, ExamMode mode, Timetable timetable) {
        SeatingPlan seating = new SeatingPlan();
        DutyRoster duties = new DutyRoster();
        WorkloadLedger ledger = new WorkloadLedger();
        List<String> problems = new ArrayList<>();

        Map<String, List<ExamSlot>> studentSittings = new HashMap<>();
        Map<String, List<ExamSlot>> facultyDuties = new HashMap<>();

        for (ExamSlot slot : timetable.slots()) {
            List<Student> cohort = data.studentsFor(slot.getPaperCode());
            List<String> rooms = timetable.roomsFor(slot.getId());
            if (rooms.isEmpty()) {
                problems.add("No room was reserved for " + slot.getPaperCode() + " (" + slot.getId() + ")");
                continue;
            }

            Set<String> roomsUsed = seat(slot, cohort, rooms, data, seating, studentSittings, problems);
            staff(slot, roomsUsed, data, duties, ledger, facultyDuties, problems);
        }

        ExamPlan plan = new ExamPlan(data, mode, timetable.slots(), seating, duties);
        return new AllocationResult(plan, List.copyOf(problems));
    }

    private Set<String> seat(ExamSlot slot, List<Student> cohort, List<String> rooms, ExamData data,
                             SeatingPlan seating, Map<String, List<ExamSlot>> studentSittings,
                             List<String> problems) {
        Set<String> roomsUsed = new LinkedHashSet<>();
        int next = 0;

        for (String roomId : rooms) {
            if (next == cohort.size()) {
                break;
            }
            Room room = data.room(roomId);
            if (room == null) {
                problems.add("Room " + roomId + " is reserved for " + slot.getPaperCode()
                        + " (" + slot.getId() + ") but is not in the room data");
                continue;
            }
            int capacity = data.capacityOf(roomId, config);
            int seatedHere = 0;

            for (int row = 1; row <= room.getRowCount() && next < cohort.size() && seatedHere < capacity; row++) {
                for (int column = 1; column <= room.getColumnCount() && next < cohort.size() && seatedHere < capacity;
                     column++) {
                    Student student = cohort.get(next);
                    List<ExamSlot> already = studentSittings.computeIfAbsent(student.getId(), key -> new ArrayList<>());
                    if (already.stream().anyMatch(slot::overlaps)) {
                        problems.add("Student " + student.getId() + " is already sitting a paper that overlaps "
                                + slot.getPaperCode() + " (" + slot.getStartTime() + "-" + slot.getEndTime()
                                + " on " + slot.getDate() + ")");
                        next++;
                        continue;
                    }
                    seating.place(slot.getId(), new Seat(roomId, row, column, student.getId()));
                    already.add(slot);
                    roomsUsed.add(roomId);
                    seatedHere++;
                    next++;
                }
            }
        }

        if (next < cohort.size()) {
            problems.add((cohort.size() - next) + " of " + cohort.size() + " students sitting "
                    + slot.getPaperCode() + " (" + slot.getId() + ") have nowhere to sit: the "
                    + rooms.size() + " reserved rooms are full");
        }
        return roomsUsed;
    }

    private void staff(ExamSlot slot, Set<String> roomsUsed, ExamData data, DutyRoster duties,
                       WorkloadLedger ledger, Map<String, List<ExamSlot>> facultyDuties, List<String> problems) {
        for (String roomId : roomsUsed) {
            Faculty chosen = null;
            Map<String, String> refused = new LinkedHashMap<>();

            for (Faculty candidate : leastLoadedFirst(data.faculty(), ledger, slot, data)) {
                List<ExamSlot> covered = facultyDuties.computeIfAbsent(candidate.getId(), key -> new ArrayList<>());
                Optional<String> refusal = eligibility.refusal(candidate, slot, covered,
                        ledger.dutiesFor(candidate.getId()));
                if (refusal.isPresent()) {
                    refused.put(candidate.getId(), refusal.get());
                    continue;
                }
                duties.assign(new Assignment(slot.getId(), roomId, candidate.getId(), DutyOrigin.RECOMMENDED));
                covered.add(slot);
                ledger.record(candidate.getId());
                chosen = candidate;
                break;
            }
            if (chosen == null) {
                problems.add("No invigilator is free for room " + roomId + " during " + slot.getPaperCode()
                        + " (" + slot.getStartTime() + "-" + slot.getEndTime() + " on " + slot.getDate() + ")"
                        + whyNot(refused));
            }
        }
    }

    /**
     * The refusals behind an unstaffed room, so the problem says what to change.
     *
     * <p>Only a handful are listed: a coordinator reading that forty colleagues were each busy
     * learns no more than from reading three, and the whole list would bury the message.
     */
    private static String whyNot(Map<String, String> refused) {
        if (refused.isEmpty()) {
            return ": there are no faculty records to draw on";
        }
        List<String> first = refused.entrySet().stream()
                .limit(3)
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .toList();
        String more = refused.size() > first.size() ? ", and " + (refused.size() - first.size()) + " more" : "";
        return ": " + String.join("; ", first) + more;
    }

    /**
     * Spreads duties as the plan is built, and breaks ties by id so runs are repeatable.
     *
     * <p>Department is only a tie-break, and only when the policy asks for it: preferring the
     * paper's own department ahead of load would undo the spreading this sort exists for.
     */
    private List<Faculty> leastLoadedFirst(List<Faculty> faculty, WorkloadLedger ledger,
                                           ExamSlot slot, ExamData data) {
        String paperDepartment = departmentOf(slot, data);
        Comparator<Faculty> order = eligibility.leastLoadedFirst(
                member -> ledger.dutiesFor(member.getId()));
        if (eligibility.policy().preferOwnDepartment() && paperDepartment != null) {
            // Inserted ahead of the id tie-break but behind load and share, so preferring a
            // department never outweighs spreading the work.
            order = Comparator
                    .comparingDouble((Faculty member) ->
                            eligibility.relativeLoad(member, ledger.dutiesFor(member.getId())))
                    .thenComparingDouble(member -> -eligibility.expectedShareOf(member))
                    .thenComparing(member -> paperDepartment.equalsIgnoreCase(member.getDepartment()) ? 0 : 1)
                    .thenComparing(Faculty::getId);
        }
        return faculty.stream().sorted(order).toList();
    }

    /**
     * The department a paper belongs to, taken from whoever is sitting it.
     *
     * <p>The exam data carries no department of its own for a paper — only students and faculty
     * have one — so the cohort stands in for it. For a paper sat by one cohort that is exactly
     * right, and for a shared paper it picks one of the departments involved, which is good
     * enough for a tie-break and would not be for a rule.
     */
    private static String departmentOf(ExamSlot slot, ExamData data) {
        return data.studentsFor(slot.getPaperCode()).stream()
                .map(Student::getDepartment)
                .filter(department -> department != null && !department.isBlank())
                .findFirst()
                .orElse(null);
    }
}
