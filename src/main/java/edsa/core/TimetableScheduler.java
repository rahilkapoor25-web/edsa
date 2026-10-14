package edsa.core;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

            Set<String> busyRooms = new HashSet<>();
            clashing.forEach(slot -> busyRooms.addAll(roomsBySlot.getOrDefault(slot.getId(), List.of())));

            List<String> reserved = reserveRooms(cohort.size(), largestFirst, busyRooms, data);
            if (reserved == null) {
                lastReason = "the free rooms at " + start + " cannot seat " + cohort.size() + " students";
                continue;
            }

            int invigilatorsBusy = busyRooms.size();
            if (invigilatorsBusy + reserved.size() > data.faculty().size()) {
                lastReason = "only " + data.faculty().size() + " invigilators exist and "
                        + (invigilatorsBusy + reserved.size()) + " rooms would be running at " + start;
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

    /** Enough free rooms, largest first, to hold the cohort — or null when they do not exist. */
    private List<String> reserveRooms(int cohortSize, List<Room> largestFirst, Set<String> busyRooms, ExamData data) {
        List<String> reserved = new ArrayList<>();
        int seats = 0;
        for (Room room : largestFirst) {
            if (busyRooms.contains(room.getId())) {
                continue;
            }
            reserved.add(room.getId());
            seats += data.capacityOf(room.getId(), config);
            if (seats >= cohortSize) {
                return reserved;
            }
        }
        return null;
    }
}
