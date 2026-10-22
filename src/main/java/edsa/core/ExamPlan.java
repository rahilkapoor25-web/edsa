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

    /** The timetable as rows: one per paper, with its rooms and invigilators counted. */
    public List<ScheduledPaper> scheduledPapers() {
        List<RoomSitting> all = sittings();
        List<ScheduledPaper> rows = new ArrayList<>();
        for (ExamSlot slot : schedule) {
            List<RoomSitting> forSlot = all.stream()
                    .filter(sitting -> sitting.slot().getId().equals(slot.getId()))
                    .toList();
            long invigilators = forSlot.stream()
                    .flatMap(sitting -> sitting.invigilators().stream())
                    .map(Faculty::getId)
                    .distinct()
                    .count();
            rows.add(new ScheduledPaper(slot.getId(), slot.getPaperCode(), slot.getPaperName(),
                    slot.getDate().toString(),
                    slot.getStartTime() + "\u2013" + slot.getEndTime(),
                    slot.getDurationLabel(),
                    forSlot.stream().mapToInt(RoomSitting::seated).sum(),
                    forSlot.size(),
                    (int) invigilators));
        }
        return rows;
    }

    /** The rooms this plan actually uses, in the order the room data lists them. */
    public List<Room> roomsUsed() {
        Set<String> used = new LinkedHashSet<>();
        seating.slotIds().forEach(slotId -> seating.seatsFor(slotId).forEach(seat -> used.add(seat.roomId())));
        return data.rooms().stream().filter(room -> used.contains(room.getId())).toList();
    }

    /**
     * The invigilators whose name contains the given text, each with every duty they hold.
     *
     * <p>Matching is deliberately forgiving — trimmed, case-insensitive, and on any part of
     * the name — because somebody looking for their own room types "aman", not
     * "Dr. Aman Verma". A blank search matches nobody rather than everybody, so clearing the
     * box puts the page back as it was instead of dumping the whole roster.
     *
     * <p>Every room in the result comes from the duty roster. A professor's room is never
     * guessed from their name or their department.
     */
    public List<DutySearchResult> invigilatorsNamed(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String wanted = query.trim().toLowerCase();

        return data.faculty().stream()
                .filter(member -> member.getName() != null
                        && member.getName().toLowerCase().contains(wanted))
                .sorted(Comparator.comparing(Faculty::getName).thenComparing(Faculty::getId))
                .map(member -> new DutySearchResult(member, dutiesOf(member)))
                .toList();
    }

    /** Everything one invigilator is down for, earliest first. */
    private List<FacultyDuty> dutiesOf(Faculty member) {
        List<FacultyDuty> held = new ArrayList<>();
        for (Assignment duty : duties.forFaculty(member.getId())) {
            ExamSlot slot = slotsById.get(duty.slotId());
            Room room = data.room(duty.roomId());
            if (slot == null || room == null) {
                continue;
            }
            held.add(new FacultyDuty(member, slot, room, seatedIn(duty.slotId(), duty.roomId())));
        }
        held.sort(Comparator.comparing((FacultyDuty held2) -> held2.slot().getDate())
                .thenComparing(one -> one.slot().getStartTime())
                .thenComparing(one -> one.room().getId()));
        return List.copyOf(held);
    }

    private int seatedIn(String slotId, String roomId) {
        return (int) seating.seatsFor(slotId).stream()
                .filter(seat -> seat.roomId().equals(roomId))
                .count();
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
