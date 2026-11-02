package edsa.web;

import edsa.core.Assignment;
import edsa.core.DutyDecision;
import edsa.core.DutyEligibility;
import edsa.core.DutyOrigin;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;
import edsa.core.Faculty;
import edsa.core.SeniorityLadder;
import edsa.data.DutyDecisionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reviewing, adjusting and approving what the allocator recommended.
 *
 * <p>Nothing here decides who should invigilate — that is {@link edsa.core.GreedyAllocator}'s
 * job and {@link DutyEligibility}'s. This sequences the human half: a coordinator confirming a
 * recommendation, putting somebody else in, or taking a decision back. Every rule it applies it
 * asks the core for, which is why a hand-made assignment that breaks a rule is refused here with
 * the core's own words rather than a message invented in the web layer.
 */
@Service
public class DutyWorkflow {

    private final DutyDecisionRepository decisions;
    private final Workspace workspace;

    public DutyWorkflow(DutyDecisionRepository decisions, Workspace workspace) {
        this.decisions = decisions;
        this.workspace = workspace;
    }

    /** One room's duty as the page shows it: who, from where, and what rank they hold. */
    public record DutyRow(ExamSlot slot, String roomId, Faculty member, DutyOrigin origin,
                          int dutiesHeld, boolean senior, String designationLabel) {

        public boolean isUnstaffed() {
            return member == null;
        }

        public boolean awaitingReview() {
            return member != null && origin == DutyOrigin.RECOMMENDED;
        }
    }

    /** Every room-sitting in the plan, staffed or not, in timetable order. */
    public List<DutyRow> rows() {
        ExamPlan plan = plan();
        if (plan == null) {
            return List.of();
        }
        SeniorityLadder ladder = workspace.ladder();
        List<DutyRow> rows = new ArrayList<>();
        for (ExamSlot slot : plan.schedule()) {
            for (String roomId : roomsOf(plan, slot)) {
                Optional<Assignment> duty = plan.duties().forSitting(slot.getId(), roomId);
                Faculty member = duty.map(assignment -> plan.data().facultyMember(assignment.facultyId()))
                        .orElse(null);
                rows.add(new DutyRow(slot, roomId, member,
                        duty.map(Assignment::origin).orElse(DutyOrigin.RECOMMENDED),
                        duty.map(assignment -> plan.duties().forFaculty(assignment.facultyId()).size()).orElse(0),
                        member != null && ladder.isSenior(member),
                        member == null ? "" : labelFor(ladder, member)));
            }
        }
        return rows;
    }

    /**
     * The rooms a sitting uses, taken from the seating rather than the roster.
     *
     * <p>A room with students in it and nobody invigilating has to appear on this page — it is
     * the single most important row on it — and reading the rooms off the duty roster would
     * leave exactly that room out.
     */
    private List<String> roomsOf(ExamPlan plan, ExamSlot slot) {
        List<String> rooms = new ArrayList<>();
        plan.sittings().stream()
                .filter(sitting -> sitting.slot().getId().equals(slot.getId()))
                .forEach(sitting -> rooms.add(sitting.room().getId()));
        plan.duties().forSlot(slot.getId()).forEach(duty -> {
            if (!rooms.contains(duty.roomId())) {
                rooms.add(duty.roomId());
            }
        });
        return rooms;
    }

    private String labelFor(SeniorityLadder ladder, Faculty member) {
        return ladder.find(member.getDesignation())
                .map(edsa.core.Designation::label)
                .orElse(member.getDesignation() == null ? "Not given" : member.getDesignation() + " (unrecognised)");
    }

    /** Approves one recommendation, leaving the invigilator as recommended. */
    @Transactional
    public void approve(String slotId, String roomId, String decidedBy) {
        ExamPlan plan = requirePlan();
        Assignment duty = plan.duties().forSitting(slotId, roomId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Room " + roomId + " has no recommended invigilator to approve"));
        decisions.save(DutyDecision.approve(slotId, roomId, duty.facultyId(), decidedBy, LocalDateTime.now()));
        workspace.compute();
    }

    /** Approves every recommendation still awaiting review, in one transaction. */
    @Transactional
    public int approveAllRecommended(String decidedBy) {
        ExamPlan plan = requirePlan();
        LocalDateTime now = LocalDateTime.now();
        int approved = 0;
        for (Assignment duty : List.copyOf(plan.duties().all())) {
            if (duty.origin() == DutyOrigin.RECOMMENDED) {
                decisions.save(DutyDecision.approve(duty.slotId(), duty.roomId(), duty.facultyId(),
                        decidedBy, now));
                approved++;
            }
        }
        workspace.compute();
        return approved;
    }

    /**
     * Puts a named colleague in a room by hand.
     *
     * <p>Refused when the core says that colleague cannot take it. A coordinator is allowed to
     * overrule the allocator's preference — that is the point of the control — but not to put
     * somebody in two rooms at once or on duty while they are on confirmed leave, because the
     * result would be a plan that fails its own rule check the moment it is recomputed.
     */
    @Transactional
    public void assignByHand(String slotId, String roomId, String facultyId, String decidedBy, String note) {
        ExamPlan plan = requirePlan();
        ExamSlot slot = plan.slot(slotId);
        if (slot == null) {
            throw new IllegalArgumentException("That sitting is not in the current plan");
        }
        Faculty member = plan.data().facultyMember(facultyId);
        if (member == null) {
            throw new IllegalArgumentException("There is no faculty record with id " + facultyId);
        }

        DutyEligibility eligibility = workspace.eligibility();
        List<ExamSlot> covered = plan.duties().forFaculty(facultyId).stream()
                .filter(duty -> !(duty.slotId().equals(slotId) && duty.roomId().equals(roomId)))
                .map(duty -> plan.slot(duty.slotId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        Optional<String> refusal = eligibility.refusal(member, slot, covered, covered.size());
        if (refusal.isPresent()) {
            throw new IllegalArgumentException(member.getName() + " cannot take that duty: " + refusal.get());
        }

        decisions.save(DutyDecision.assignByHand(slotId, roomId, facultyId, decidedBy,
                LocalDateTime.now(), note));
        workspace.compute();
    }

    /** Takes a decision back, so the room returns to whatever the allocator recommends. */
    @Transactional
    public void clearDecision(String slotId, String roomId) {
        decisions.clear(slotId, roomId);
        workspace.compute();
    }

    public int decisionCount() {
        return decisions.count();
    }

    private ExamPlan plan() {
        return workspace.hasPlan() ? workspace.result().plan() : null;
    }

    private ExamPlan requirePlan() {
        ExamPlan plan = plan();
        if (plan == null) {
            throw new IllegalArgumentException("Import data and compute a plan first");
        }
        return plan;
    }
}
