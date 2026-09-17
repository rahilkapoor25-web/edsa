package edsa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** A seating plan and duty roster, together with the data they refer to. */
public final class ExamPlan {

    private final ExamData data;
    private final SeatingPlan seating;
    private final DutyRoster duties;

    public ExamPlan(ExamData data, SeatingPlan seating, DutyRoster duties) {
        this.data = data;
        this.seating = seating;
        this.duties = duties;
    }

    public ExamData data() {
        return data;
    }

    public SeatingPlan seating() {
        return seating;
    }

    public DutyRoster duties() {
        return duties;
    }

    /** The plan read as a list of sittings, in timetable order. */
    public List<RoomSitting> sittings() {
        return sittings(roomId -> true);
    }

    /** The same, narrowed to one room. */
    public List<RoomSitting> sittingsIn(String roomId) {
        return sittings(roomId::equals);
    }

    private List<RoomSitting> sittings(Predicate<String> wanted) {
        List<RoomSitting> sittings = new ArrayList<>();
        for (ExamSlot slot : data.slots()) {
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

    /** The rooms this plan actually uses, in the order the room file lists them. */
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
                .map(duty -> data.slot(duty.slotId()))
                .sorted(Comparator.comparing(ExamSlot::getDate).thenComparing(ExamSlot::getStartTime))
                .toList();
    }
}
