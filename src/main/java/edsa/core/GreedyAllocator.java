package edsa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Seats the students and staffs the rooms for a timetable the scheduler has already placed.
 *
 * <p>A cohort is split across the rooms reserved for its sitting, filling each room's grid row
 * by row until the room is at capacity. When the rest of the cohort fits with a gap around
 * everybody, they sit on a checkerboard instead. Each used room then gets two invigilators who
 * are free at that time and not from the paper's department: the least-loaded Professor, and
 * the least-loaded junior as their partner. That keeps duties spread without a second pass.
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
            // Spread out whenever the rest of the cohort fits on the checkerboard; otherwise pack.
            boolean spaced = cohort.size() - next <= data.spacedCapacityOf(roomId, config);
            int seatedHere = 0;

            for (int row = 1; row <= room.getRowCount() && next < cohort.size() && seatedHere < capacity; row++) {
                for (int column = 1; column <= room.getColumnCount() && next < cohort.size() && seatedHere < capacity;
                     column++) {
                    if (spaced && !ExamData.isSpacedSeat(row, column)) {
                        continue;
                    }
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

    /**
     * Two invigilators per room: a Professor first, so a junior is never left alone, then a
     * partner — a junior when one is free, keeping the Professors for the rooms that need them.
     * Nobody from the paper's own department is considered.
     */
    private void staff(ExamSlot slot, Set<String> roomsUsed, ExamData data, DutyRoster duties,
                       WorkloadLedger ledger, Map<String, List<ExamSlot>> facultyDuties, List<String> problems) {
        String department = data.departmentOf(slot.getId());
        String outside = department == null ? "" : " outside " + department;
        String when = slot.getPaperCode() + " (" + slot.getStartTime() + "-" + slot.getEndTime()
                + " on " + slot.getDate() + ")";
        for (String roomId : roomsUsed) {
            Faculty senior = firstFree(data, ledger, facultyDuties, slot, department, Faculty::isSenior);
            if (senior == null) {
                problems.add("No Professor" + outside + " is free for room " + roomId + " during " + when);
                continue;
            }
            assign(slot, roomId, senior, duties, ledger, facultyDuties);
            Faculty partner = firstFree(data, ledger, facultyDuties, slot, department, member -> !member.isSenior());
            if (partner == null) {
                partner = firstFree(data, ledger, facultyDuties, slot, department, member -> true);
            }
            if (partner == null) {
                problems.add("No second invigilator" + outside + " is free for room " + roomId + " during " + when);
                continue;
            }
            assign(slot, roomId, partner, duties, ledger, facultyDuties);
        }
    }

    private static Faculty firstFree(ExamData data, WorkloadLedger ledger, Map<String, List<ExamSlot>> facultyDuties,
                                     ExamSlot slot, String department, Predicate<Faculty> wanted) {
        for (Faculty candidate : leastLoadedFirst(data.faculty(), ledger)) {
            if (!wanted.test(candidate) || candidate.belongsTo(department)) {
                continue;
            }
            if (facultyDuties.getOrDefault(candidate.getId(), List.of()).stream().noneMatch(slot::overlaps)) {
                return candidate;
            }
        }
        return null;
    }

    private static void assign(ExamSlot slot, String roomId, Faculty member, DutyRoster duties,
                               WorkloadLedger ledger, Map<String, List<ExamSlot>> facultyDuties) {
        duties.assign(new Assignment(slot.getId(), roomId, member.getId()));
        facultyDuties.computeIfAbsent(member.getId(), key -> new ArrayList<>()).add(slot);
        ledger.record(member.getId());
    }

    /** Spreads duties as the plan is built, and breaks ties by id so runs are repeatable. */
    private static List<Faculty> leastLoadedFirst(List<Faculty> faculty, WorkloadLedger ledger) {
        return faculty.stream()
                .sorted(Comparator.comparingInt((Faculty member) -> ledger.dutiesFor(member.getId()))
                        .thenComparing(Faculty::getId))
                .toList();
    }
}
