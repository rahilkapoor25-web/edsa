package edsa.core;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Builds the first plan: hardest slot first, students onto the room grid, then an invigilator
 * per room. Every candidate placement is checked against the hard rules before it is kept, so
 * the plan this returns is legal — but the duties are uneven, which is the improver's job.
 */
public final class GreedyAllocator {

    private final PlanChecker hardRules;

    public GreedyAllocator() {
        this(PlanChecker.standard());
    }

    public GreedyAllocator(PlanChecker hardRules) {
        this.hardRules = hardRules;
    }

    public ExamPlan allocate(ExamData data) {
        ExamPlan plan = new ExamPlan(data, new SeatingPlan(), new DutyRoster());
        WorkloadLedger ledger = new WorkloadLedger();

        Map<String, List<Student>> cohorts = new HashMap<>();
        data.slots().forEach(slot -> cohorts.put(slot.getId(), data.studentsFor(slot.getPaperCode())));
        List<Room> largestFirst = data.rooms().stream()
                .sorted(Comparator.comparingInt(Room::capacity).reversed().thenComparing(Room::getId))
                .toList();

        PriorityQueue<ExamSlot> hardestFirst = new PriorityQueue<>(mostConstrainedFirst(cohorts));
        hardestFirst.addAll(data.slots());
        while (!hardestFirst.isEmpty()) {
            ExamSlot slot = hardestFirst.poll();
            seat(plan, slot, cohorts.get(slot.getId()), largestFirst);
            invigilate(plan, slot, ledger);
        }
        return plan;
    }

    /** The slot with the most students to place is the one with the least room to manoeuvre. */
    private static Comparator<ExamSlot> mostConstrainedFirst(Map<String, List<Student>> cohorts) {
        return Comparator.comparingInt((ExamSlot slot) -> cohorts.get(slot.getId()).size())
                .reversed()
                .thenComparing(ExamSlot::getId);
    }

    private void seat(ExamPlan plan, ExamSlot slot, List<Student> cohort, List<Room> rooms) {
        int placed = 0;
        for (Room room : rooms) {
            if (placed == cohort.size()) {
                break;
            }
            placed = fill(plan, slot, room, cohort, placed);
        }
        if (placed < cohort.size()) {
            throw new CapacityException("slot " + slot.getId() + " (" + slot.getPaperCode() + "): "
                    + (cohort.size() - placed) + " of " + cohort.size() + " students have nowhere to sit");
        }
    }

    private int fill(ExamPlan plan, ExamSlot slot, Room room, List<Student> cohort, int from) {
        int placed = from;
        for (int row = 1; row <= room.getRowCount(); row++) {
            for (int column = 1; column <= room.getColumnCount(); column++) {
                if (placed == cohort.size()) {
                    return placed;
                }
                Seat candidate = new Seat(room.getId(), row, column, cohort.get(placed).getId());
                plan.seating().place(slot.getId(), candidate);
                if (hardRules.isLegal(plan)) {
                    placed++;
                } else {
                    plan.seating().remove(slot.getId(), candidate);
                }
            }
        }
        return placed;
    }

    private void invigilate(ExamPlan plan, ExamSlot slot, WorkloadLedger ledger) {
        for (String roomId : roomsUsed(plan, slot)) {
            Faculty chosen = null;
            for (Faculty candidate : leastLoadedFirst(plan.data().faculty(), ledger)) {
                Assignment duty = new Assignment(slot.getId(), roomId, candidate.getId());
                plan.duties().assign(duty);
                if (hardRules.isLegal(plan)) {
                    chosen = candidate;
                    break;
                }
                plan.duties().remove(duty);
            }
            if (chosen == null) {
                throw new NoInvigilatorAvailableException(
                        "no invigilator can take room " + roomId + " in slot " + slot.getId());
            }
            ledger.record(chosen.getId());
        }
    }

    private static Set<String> roomsUsed(ExamPlan plan, ExamSlot slot) {
        Set<String> roomIds = new LinkedHashSet<>();
        plan.seating().seatsFor(slot.getId()).forEach(seat -> roomIds.add(seat.roomId()));
        return roomIds;
    }

    /** Spreads duties as the plan is built; the improver makes the spread fair afterwards. */
    private static List<Faculty> leastLoadedFirst(List<Faculty> faculty, WorkloadLedger ledger) {
        return faculty.stream()
                .sorted(Comparator.comparingInt((Faculty member) -> ledger.dutiesFor(member.getId()))
                        .thenComparing(Faculty::getId))
                .toList();
    }
}
