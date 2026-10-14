package edsa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 */
public final class GreedyAllocator {

    private final SchedulingConfig config;

    public GreedyAllocator(SchedulingConfig config) {
        this.config = config;
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
            for (Faculty candidate : leastLoadedFirst(data.faculty(), ledger)) {
                List<ExamSlot> covered = facultyDuties.computeIfAbsent(candidate.getId(), key -> new ArrayList<>());
                if (covered.stream().anyMatch(slot::overlaps)) {
                    continue;
                }
                duties.assign(new Assignment(slot.getId(), roomId, candidate.getId()));
                covered.add(slot);
                ledger.record(candidate.getId());
                chosen = candidate;
                break;
            }
            if (chosen == null) {
                problems.add("No invigilator is free for room " + roomId + " during " + slot.getPaperCode()
                        + " (" + slot.getStartTime() + "-" + slot.getEndTime() + " on " + slot.getDate() + ")");
            }
        }
    }

    /** Spreads duties as the plan is built, and breaks ties by id so runs are repeatable. */
    private static List<Faculty> leastLoadedFirst(List<Faculty> faculty, WorkloadLedger ledger) {
        return faculty.stream()
                .sorted(Comparator.comparingInt((Faculty member) -> ledger.dutiesFor(member.getId()))
                        .thenComparing(Faculty::getId))
                .toList();
    }
}
