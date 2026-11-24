package edsa.core;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns declared exams into sittings with real start and end times.
 *
 * <p>Papers are placed hardest first — the largest cohort has the least room to manoeuvre.
 * Each paper is tried at the earliest time that works, so sittings pack together rather than
 * leaving gaps, and several papers run in parallel whenever their students, rooms and
 * invigilators allow it. Times are compared as intervals, start-inclusive and end-exclusive.
 */
public final class TimetableScheduler {

    private final SchedulingConfig config;

    public TimetableScheduler(SchedulingConfig config) {
        this.config = config;
    }

    public TimetableScheduler() {
        this(SchedulingConfig.defaults());
    }

    public SchedulingResult schedule(ExamData data, ExamMode mode) {
        List<ExamSlot> placed = new ArrayList<>();
        Map<String, List<String>> roomsBySlot = new LinkedHashMap<>();
        Map<String, List<String>> cohortBySlot = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();

        List<Room> largestFirst = data.rooms().stream()
                .sorted(Comparator.comparingInt((Room room) -> data.capacityOf(room.getId(), config))
                        .reversed()
                        .thenComparing(Room::getId))
                .toList();

        for (Exam exam : hardestFirst(data)) {
            List<Student> cohort = data.studentsFor(exam.getSubjectCode());
            if (cohort.isEmpty()) {
                problems.add("No student is registered for " + exam.getSubjectCode()
                        + " (" + exam.getId() + "), so it was not scheduled");
                continue;
            }
            int duration = exam.durationUnder(mode);
            if (duration <= 0) {
                problems.add(exam.getSubjectCode() + " (" + exam.getId() + ") has an invalid duration of "
                        + duration + " minutes");
                continue;
            }

            String failure = place(exam, cohort, duration, data, largestFirst,
                    placed, roomsBySlot, cohortBySlot);
            if (failure != null) {
                problems.add(failure);
            }
        }
        return new SchedulingResult(new Timetable(List.copyOf(placed), Map.copyOf(roomsBySlot)), List.copyOf(problems));
    }

    /** The paper with the most candidates is the one with the least room to manoeuvre. */
    private List<Exam> hardestFirst(ExamData data) {
        return data.exams().stream()
                .sorted(Comparator.comparing(Exam::getDate)
                        .thenComparing(exam -> -data.studentsFor(exam.getSubjectCode()).size())
                        .thenComparing(Exam::getId))
                .toList();
    }

    private String place(Exam exam, List<Student> cohort, int duration, ExamData data,
                         List<Room> largestFirst, List<ExamSlot> placed,
                         Map<String, List<String>> roomsBySlot, Map<String, List<String>> cohortBySlot) {
        Set<String> cohortIds = new HashSet<>();
        cohort.forEach(student -> cohortIds.add(student.getId()));
        String lastReason = null;

        for (LocalTime start : candidateStarts(exam, placed)) {
            ExamSlot candidate = new ExamSlot(exam.getId(), exam.getSubjectCode(), exam.getSubjectName(),
                    exam.getDate(), start, duration);

            if (candidate.getEndTime().isAfter(config.dayEnd())) {
                lastReason = "it would finish at " + candidate.getEndTime() + ", after the hall closes at "
                        + config.dayEnd();
                continue;
            }
            if (start.isBefore(config.dayStart())) {
                lastReason = "it would start at " + start + ", before the hall opens at " + config.dayStart();
                continue;
            }

            List<ExamSlot> clashing = placed.stream().filter(candidate::overlaps).toList();

            String studentClash = sharedStudent(clashing, cohortIds, cohortBySlot);
            if (studentClash != null) {
                lastReason = "student " + studentClash + " is already sitting another paper then";
                continue;
            }

            // A room is busy until its sitting ends and it has been turned round for the next.
            Set<String> busyRooms = new HashSet<>();
            placed.stream()
                    .filter(slot -> candidate.overlaps(slot, config.turnaroundMinutes()))
                    .forEach(slot -> busyRooms.addAll(roomsBySlot.getOrDefault(slot.getId(), List.of())));

            List<String> reserved = reserveRooms(cohort.size(), largestFirst, busyRooms, data);
            if (reserved == null) {
                lastReason = "the free rooms at " + start + " cannot seat " + cohort.size() + " students";
                continue;
            }

            int roomsRunning = reserved.size() + clashing.stream()
                    .mapToInt(slot -> roomsBySlot.getOrDefault(slot.getId(), List.of()).size())
                    .sum();
            String shortfall = staffingShortfall(exam, start, roomsRunning, reserved.size(), data);
            if (shortfall != null) {
                lastReason = shortfall;
                continue;
            }

            placed.add(candidate);
            roomsBySlot.put(candidate.getId(), reserved);
            cohortBySlot.put(candidate.getId(), List.copyOf(cohortIds));
            return null;
        }
        return exam.getSubjectCode() + " (" + exam.getId() + ") could not be scheduled on " + exam.getDate()
                + (lastReason == null ? "" : ": " + lastReason);
    }

    /**
     * Whether the staff exist for every room running at that time: two invigilators per room, a
     * Professor in each, and — for this paper — enough of both from outside its own department.
     *
     * <p>shortcut: counts heads rather than solving who goes where, so two papers whose
     * departments exclude each other's staff can still pass here; the allocator then reports any
     * room it cannot staff. Upgrade to a matching if real sessions hit that.
     */
    private String staffingShortfall(Exam exam, LocalTime start, int roomsRunning, int rooms, ExamData data) {
        int perRoom = SchedulingConfig.INVIGILATORS_PER_ROOM;
        long professors = data.faculty().stream().filter(Faculty::isSenior).count();
        if (data.faculty().size() < perRoom * roomsRunning) {
            return "only " + data.faculty().size() + " invigilators exist and the " + roomsRunning
                    + " rooms running at " + start + " need " + perRoom * roomsRunning;
        }
        if (professors < roomsRunning) {
            return "only " + professors + " Professors exist and each of the " + roomsRunning
                    + " rooms running at " + start + " needs one";
        }
        String department = exam.getDepartment();
        if (department == null) {
            return null;
        }
        List<Faculty> eligible = data.faculty().stream().filter(member -> !member.belongsTo(department)).toList();
        long eligibleProfessors = eligible.stream().filter(Faculty::isSenior).count();
        if (eligibleProfessors < rooms) {
            return "only " + eligibleProfessors + " Professors outside " + department
                    + " may invigilate it and its " + rooms + " rooms need one each";
        }
        if (eligible.size() < perRoom * rooms) {
            return "only " + eligible.size() + " invigilators outside " + department
                    + " may invigilate it and its " + rooms + " rooms need " + perRoom * rooms;
        }
        return null;
    }

    /**
     * The times worth trying: a fixed start when the office set one, otherwise the opening
     * time and the moment each sitting already placed that day finishes.
     */
    private List<LocalTime> candidateStarts(Exam exam, List<ExamSlot> placed) {
        if (exam.getPermittedStart().isPresent()) {
            return List.of(exam.getPermittedStart().get());
        }
        LocalDate date = exam.getDate();
        Set<LocalTime> starts = new TreeSet<>();
        starts.add(config.dayStart());
        placed.stream()
                .filter(slot -> slot.getDate().equals(date))
                .forEach(slot -> {
                    starts.add(slot.getStartTime());
                    starts.add(slot.getEndTime());
                    starts.add(slot.getEndTime().plusMinutes(config.turnaroundMinutes()));
                });
        return starts.stream().filter(start -> !start.isBefore(config.dayStart())).toList();
    }

    private String sharedStudent(List<ExamSlot> clashing, Set<String> cohortIds,
                                 Map<String, List<String>> cohortBySlot) {
        for (ExamSlot slot : clashing) {
            for (String studentId : cohortBySlot.getOrDefault(slot.getId(), List.of())) {
                if (cohortIds.contains(studentId)) {
                    return studentId;
                }
            }
        }
        return null;
    }

    /**
     * The smallest free room that seats the cohort spaced out, else the smallest that seats it
     * at all, so the big rooms stay free for papers running alongside. Only a cohort no single
     * room holds is split, across the largest free rooms first. Null when even that fails.
     */
    private List<String> reserveRooms(int cohortSize, List<Room> largestFirst, Set<String> busyRooms, ExamData data) {
        List<Room> free = largestFirst.stream().filter(room -> !busyRooms.contains(room.getId())).toList();
        Comparator<Room> smallestFirst = Comparator
                .comparingInt((Room room) -> data.capacityOf(room.getId(), config))
                .thenComparing(Room::getId);
        Optional<Room> single = free.stream()
                .filter(room -> data.spacedCapacityOf(room.getId(), config) >= cohortSize)
                .min(smallestFirst)
                .or(() -> free.stream()
                        .filter(room -> data.capacityOf(room.getId(), config) >= cohortSize)
                        .min(smallestFirst));
        if (single.isPresent()) {
            return List.of(single.get().getId());
        }

        List<String> reserved = new ArrayList<>();
        int seats = 0;
        for (Room room : free) {
            reserved.add(room.getId());
            seats += data.capacityOf(room.getId(), config);
            if (seats >= cohortSize) {
                return reserved;
            }
        }
        return null;
    }
}
