package edsa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A computed timetable: which paper runs when, who sits where, and who invigilates — together
 * with the data and the mode it was computed from.
 */
public final class ExamPlan {

    private final ExamData data;
    private final ExamMode mode;
    private final List<ExamSlot> schedule;
    private final SeatingPlan seating;
    private final DutyRoster duties;
    private final Map<String, ExamSlot> slotsById;

    public ExamPlan(ExamData data, ExamMode mode, List<ExamSlot> schedule,
                    SeatingPlan seating, DutyRoster duties) {
        this.data = data;
        this.mode = mode;
        this.schedule = List.copyOf(schedule);
        this.seating = seating;
        this.duties = duties;
        this.slotsById = new LinkedHashMap<>();
        this.schedule.forEach(slot -> slotsById.put(slot.getId(), slot));
    }

    public ExamData data() {
        return data;
    }

    public ExamMode mode() {
        return mode;
    }

    /** The sittings, in the order the scheduler placed them. */
    public List<ExamSlot> schedule() {
        return schedule;
    }

    public ExamSlot slot(String slotId) {
        return slotsById.get(slotId);
    }

    public SeatingPlan seating() {
        return seating;
    }

    public DutyRoster duties() {
        return duties;
    }

    /** The plan read as a list of sittings, in schedule order. */
    public List<RoomSitting> sittings() {
        return sittings(roomId -> true);
    }

    /** The same, narrowed to one room. */
    public List<RoomSitting> sittingsIn(String roomId) {
        return sittings(roomId::equals);
    }

    private List<RoomSitting> sittings(Predicate<String> wanted) {
        List<RoomSitting> sittings = new ArrayList<>();
        for (ExamSlot slot : schedule) {
            Map<String, List<PlacedStudent>> byRoom = new LinkedHashMap<>();
            for (Seat seat : seating.seatsFor(slot.getId())) {
                if (wanted.test(seat.roomId())) {
                    byRoom.computeIfAbsent(seat.roomId(), key -> new ArrayList<>())
                            .add(new PlacedStudent(seat.row(), seat.column(), data.student(seat.studentId())));
                }
            }
            byRoom.forEach((roomId, students) -> sittings.add(
                    new RoomSitting(slot, data.room(roomId), List.copyOf(students),
                            invigilatorsOf(slot.getId(), roomId))));
        }
        return sittings;
    }

    private List<Faculty> invigilatorsOf(String slotId, String roomId) {
        return duties.forSlot(slotId).stream()
                .filter(duty -> duty.roomId().equals(roomId))
                .map(duty -> data.facultyMember(duty.facultyId()))
                .toList();
    }

    /** The rooms this plan actually uses, in the order the room data lists them. */
    public List<Room> roomsUsed() {
        Set<String> used = new LinkedHashSet<>();
        seating.slotIds().forEach(slotId -> seating.seatsFor(slotId).forEach(seat -> used.add(seat.roomId())));
        return data.rooms().stream().filter(room -> used.contains(room.getId())).toList();
    }

    /** Every faculty member and the duties they drew, busiest first. */
    public List<Workload> workloads() {
        return data.faculty().stream()
                .map(member -> new Workload(member, slotsFor(member.getId())))
                .sorted(Comparator.comparingInt(Workload::duties).reversed()
                        .thenComparing(workload -> workload.member().getId()))
                .toList();
    }

    private List<ExamSlot> slotsFor(String facultyId) {
        return duties.forFaculty(facultyId).stream()
                .map(duty -> slotsById.get(duty.slotId()))
                .filter(slot -> slot != null)
                .sorted(Comparator.comparing(ExamSlot::getDate).thenComparing(ExamSlot::getStartTime))
                .toList();
    }
}
