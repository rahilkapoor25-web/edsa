package edsa.web;

import edsa.core.Assignment;
import edsa.core.DutyOrigin;
import edsa.core.DutyRedemption;
import edsa.core.DutyRelief;
import edsa.core.ExamPlan;
import edsa.core.ExamSlot;
import edsa.core.Faculty;
import edsa.core.PointsBalance;
import edsa.core.PointsEntry;
import edsa.core.RedemptionStatus;
import edsa.core.ReliefDecision;
import edsa.core.RewardRules;
import edsa.core.SubstitutionRequest;
import edsa.core.SubstitutionStatus;
import edsa.data.PointsRepository;
import edsa.data.RedemptionRepository;
import edsa.data.SubstitutionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Substitute duties and the points they earn, start to finish.
 *
 * <p>Every rule this applies lives in {@link edsa.core}: whether a status may change is
 * {@link SubstitutionStatus}'s, whether somebody may decide a case is
 * {@link SubstitutionRequest#mayBeDecidedBy}'s, whether a duty may be given up for points is
 * {@link DutyRelief}'s. What is here is the sequencing, the transactions, and the translation
 * between a web request and those rules.
 *
 * <p>Three protections are worth naming, because each closes a way the scheme could be gamed.
 * Points are awarded at completion and nowhere else, so an approved substitution that never
 * happened earns nothing. Nobody decides a case they are party to, which is enforced against the
 * account's faculty id and not only its role — an administrator is also a colleague. And the
 * database, not this class, is the last word on both: a unique key on the award and a check
 * constraint on self-substitution hold even if two requests race past the checks here.
 */
@Service
public class RewardWorkflow {

    private final SubstitutionRepository substitutions;
    private final PointsRepository points;
    private final RedemptionRepository redemptions;
    private final Workspace workspace;
    private final RewardRules rules;

    public RewardWorkflow(SubstitutionRepository substitutions, PointsRepository points,
                          RedemptionRepository redemptions, Workspace workspace, RewardRules rules) {
        this.substitutions = substitutions;
        this.points = points;
        this.redemptions = redemptions;
        this.workspace = workspace;
        this.rules = rules;
    }

    /** Who is acting, and whether they may decide other people's cases. */
    public record Actor(String email, String facultyId, boolean administrator) {
    }

    public RewardRules rules() {
        return rules;
    }

    // ----- offering and covering -------------------------------------------------------

    /**
     * Offers one of the actor's own duties up for somebody else to cover.
     *
     * <p>A professor may offer only their own duty; an administrator may offer anybody's,
     * because recording an offer on behalf of a colleague who telephoned is part of the job.
     * Checked against the faculty id on the account rather than a name typed into the form.
     */
    @Transactional
    public SubstitutionRequest offer(Actor actor, String slotId, String roomId,
                                     String forFacultyId, String reason) {
        ExamPlan plan = requirePlan();
        ExamSlot slot = plan.slot(slotId);
        if (slot == null) {
            throw new IllegalArgumentException("That sitting is not in the current plan");
        }
        Assignment duty = plan.duties().forSitting(slotId, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room " + roomId + " has no duty to offer"));

        String owner = actor.administrator() && forFacultyId != null && !forFacultyId.isBlank()
                ? forFacultyId.trim()
                : actor.facultyId();
        if (owner == null) {
            throw new IllegalArgumentException("Your account is not linked to a faculty record, "
                    + "so it has no duties to offer. Ask the examination office to offer it for you.");
        }
        if (!duty.facultyId().equals(owner)) {
            throw new IllegalArgumentException("That duty belongs to " + duty.facultyId() + ", not " + owner);
        }
        if (substitutions.hasOpenOffer(slotId, roomId)) {
            throw new IllegalArgumentException("That duty has already been offered");
        }

        return substitutions.offer(SubstitutionRequest.offer(slotId, roomId, slot.getPaperCode(),
                slot.getDate().atTime(slot.getStartTime()), slot.getDate().atTime(slot.getEndTime()),
                owner, reason, actor.email(), LocalDateTime.now()));
    }

    /**
     * Volunteers the actor to cover an offered duty.
     *
     * <p>Two people clicking at once is the normal case for anything desirable, so the claim is
     * a conditional update: whoever's statement matches the still-offered row gets it, and the
     * other is told it has gone. Eligibility is checked as well — a volunteer who is already
     * invigilating at that hour would turn one unstaffed room into two.
     */
    @Transactional
    public void volunteer(Actor actor, long substitutionId) {
        SubstitutionRequest request = require(substitutionId);
        if (actor.facultyId() == null) {
            throw new IllegalArgumentException("Your account is not linked to a faculty record, "
                    + "so it cannot take a duty");
        }
        if (!request.mayBeCoveredBy(actor.facultyId())) {
            throw new IllegalArgumentException(request.originalFacultyId().equals(actor.facultyId())
                    ? "You cannot cover your own duty"
                    : "That offer is no longer open for volunteers");
        }

        ExamPlan plan = requirePlan();
        ExamSlot slot = plan.slot(request.slotId());
        Faculty volunteer = plan.data().facultyMember(actor.facultyId());
        if (slot != null && volunteer != null) {
            List<ExamSlot> covered = slotsOf(plan, actor.facultyId());
            Optional<String> refusal = workspace.eligibility().refusal(volunteer, slot, covered, covered.size());
            if (refusal.isPresent()) {
                throw new IllegalArgumentException("You cannot take that duty: " + refusal.get());
            }
        }

        if (!substitutions.claim(substitutionId, actor.facultyId(), LocalDateTime.now())) {
            throw new IllegalArgumentException("Somebody else volunteered first");
        }
    }

    /**
     * Approves a volunteer, which is the point at which the duty actually moves.
     *
     * <p>No points yet. The substitute has agreed to turn up, not turned up, and paying at
     * approval would mean an approved substitution nobody attended still earned its credit.
     */
    @Transactional
    public void approveSubstitution(Actor actor, long substitutionId) {
        SubstitutionRequest request = require(substitutionId);
        requireDecider(actor, request);

        if (!substitutions.decide(substitutionId, SubstitutionStatus.APPROVED, actor.email(),
                LocalDateTime.now(), "Approved; awaiting the duty being done")) {
            throw new IllegalArgumentException("That offer is no longer awaiting approval");
        }
        if (workspace.hasPlan()) {
            workspace.result().plan().duties().reassign(request.slotId(), request.roomId(),
                    request.volunteerFacultyId(), DutyOrigin.SUBSTITUTED);
        }
    }

    @Transactional
    public void rejectSubstitution(Actor actor, long substitutionId, String why) {
        SubstitutionRequest request = require(substitutionId);
        requireDecider(actor, request);
        if (!substitutions.decide(substitutionId, SubstitutionStatus.REJECTED, actor.email(),
                LocalDateTime.now(), why == null || why.isBlank() ? "Rejected" : why)) {
            throw new IllegalArgumentException("That offer is no longer awaiting approval");
        }
    }

    /**
     * Confirms the substitute actually invigilated, and credits them.
     *
     * <p>The one place points are created. The status update and the award are in the same
     * transaction, and the award is also protected by a unique key, so a substitution cannot be
     * completed twice nor credited twice even if two administrators click together.
     */
    @Transactional
    public int completeAndAward(Actor actor, long substitutionId) {
        SubstitutionRequest request = require(substitutionId);
        if (!actor.administrator()) {
            throw new IllegalArgumentException("Only an administrator may confirm a duty was done");
        }
        if (!request.status().mayComplete()) {
            throw new IllegalArgumentException("Only an approved substitution can be marked done");
        }
        if (actor.facultyId() != null && actor.facultyId().equals(request.volunteerFacultyId())) {
            throw new IllegalArgumentException("You cannot confirm your own substitute duty");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!substitutions.complete(substitutionId, actor.email(), now,
                "Completed; " + rules.pointsPerSubstitution() + " points awarded")) {
            throw new IllegalArgumentException("That substitution has already been completed");
        }
        boolean awarded = points.award(request.volunteerFacultyId(), rules.pointsPerSubstitution(),
                substitutionId, actor.email(), now,
                rules.pointsExpire() ? now.plusDays(rules.validityDays()) : null,
                "Covered " + request.paperCode() + " in room " + request.roomId()
                        + " for " + request.originalFacultyId());
        return awarded ? rules.pointsPerSubstitution() : 0;
    }

    @Transactional
    public void withdraw(Actor actor, long substitutionId) {
        SubstitutionRequest request = require(substitutionId);
        boolean own = actor.facultyId() != null && actor.facultyId().equals(request.originalFacultyId());
        if (!own && !actor.administrator()) {
            throw new IllegalArgumentException("Only the duty's owner or an administrator may withdraw it");
        }
        if (!substitutions.withdraw(substitutionId, "Withdrawn by " + actor.email())) {
            throw new IllegalArgumentException("That offer can no longer be withdrawn");
        }
    }

    // ----- redeeming ------------------------------------------------------------------

    /**
     * Asks to be excused one duty in exchange for points.
     *
     * <p>Checked when asked and checked again when decided, because the answer moves: who is
     * free to stand in depends on the plan, and the plan changes between the two.
     */
    @Transactional
    public DutyRedemption requestRelief(Actor actor, String slotId, String roomId, String reason) {
        ExamPlan plan = requirePlan();
        if (actor.facultyId() == null) {
            throw new IllegalArgumentException("Your account is not linked to a faculty record");
        }
        ReliefDecision decision = relief().check(plan, actor.facultyId(), slotId, roomId,
                balanceOf(actor.facultyId()), redemptions.approvedCountFor(actor.facultyId()));
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.reason());
        }
        ExamSlot slot = plan.slot(slotId);
        return redemptions.request(DutyRedemption.request(actor.facultyId(), slotId, roomId,
                slot.getPaperCode(), slot.getDate().atTime(slot.getStartTime()),
                rules.pointsToSkipOneDuty(), reason, LocalDateTime.now()));
    }

    /**
     * Grants an exemption: spends the points and moves the duty to the stand-in.
     *
     * <p>The guard runs again here, and this is the run that matters. If nobody is free any more
     * the request is rejected rather than granted with the room left empty — an unstaffed
     * examination room is a worse outcome than a disappointed colleague, and the points are not
     * taken either.
     */
    @Transactional
    public void approveRelief(Actor actor, long redemptionId) {
        DutyRedemption redemption = requireRedemption(redemptionId);
        if (!actor.administrator()) {
            throw new IllegalArgumentException("Only an administrator may approve an exemption");
        }
        if (!redemption.mayBeDecidedBy(actor.facultyId())) {
            throw new IllegalArgumentException("You cannot decide your own request");
        }

        ExamPlan plan = requirePlan();
        // The pending hold is excluded, because these are the very points being spent: counting
        // them as held as well would make every request look unaffordable at the moment of
        // approval.
        PointsBalance balance = points.balanceOf(redemption.facultyId(),
                Math.max(0, redemptions.pendingPointsFor(redemption.facultyId()) - redemption.pointsSpent()));
        ReliefDecision decision = relief().check(plan, redemption.facultyId(), redemption.slotId(),
                redemption.roomId(), balance, redemptions.approvedCountFor(redemption.facultyId()));

        if (!decision.allowed()) {
            // Left pending rather than auto-rejected, and deliberately so. Writing the rejection
            // here and then throwing would roll the rejection back with the transaction, so the
            // request would look untouched while the administrator was told it had been refused —
            // worse than either outcome on its own. Pending is also the more useful state: who is
            // free changes, so a grant that is impossible this morning may be fine tomorrow, and
            // the administrator can refuse it outright if it never will be.
            throw new IllegalArgumentException("That exemption cannot be granted at the moment: "
                    + decision.reason() + ". The request is still waiting, so it can be granted "
                    + "later if that changes, or refused outright.");
        }

        LocalDateTime now = LocalDateTime.now();
        String standIn = decision.replacementFacultyId();
        if (!redemptions.decide(redemptionId, RedemptionStatus.APPROVED, actor.email(), now,
                "Approved; " + standIn + " takes room " + redemption.roomId())) {
            throw new IllegalArgumentException("That request has already been decided");
        }
        points.redeem(redemption.facultyId(), redemption.pointsSpent(), redemptionId, actor.email(), now,
                "Excused " + redemption.paperCode() + " in room " + redemption.roomId());
        plan.duties().reassign(redemption.slotId(), redemption.roomId(), standIn, DutyOrigin.SUBSTITUTED);
    }

    @Transactional
    public void rejectRelief(Actor actor, long redemptionId, String why) {
        requireRedemption(redemptionId);
        if (!actor.administrator()) {
            throw new IllegalArgumentException("Only an administrator may decide an exemption");
        }
        if (!redemptions.decide(redemptionId, RedemptionStatus.REJECTED, actor.email(),
                LocalDateTime.now(), why == null || why.isBlank() ? "Rejected" : why)) {
            throw new IllegalArgumentException("That request has already been decided");
        }
    }

    /** An administrator's correction to a balance, which must say why. */
    @Transactional
    public void adjust(Actor actor, String facultyId, int points, String why) {
        if (!actor.administrator()) {
            throw new IllegalArgumentException("Only an administrator may correct a balance");
        }
        if (why == null || why.isBlank()) {
            throw new IllegalArgumentException("A correction must say why");
        }
        if (actor.facultyId() != null && actor.facultyId().equals(facultyId)) {
            throw new IllegalArgumentException("You cannot correct your own balance");
        }
        this.points.adjust(facultyId, points, actor.email(), LocalDateTime.now(), why);
    }

    // ----- reading --------------------------------------------------------------------

    public PointsBalance balanceOf(String facultyId) {
        return points.balanceOf(facultyId, redemptions.pendingPointsFor(facultyId));
    }

    public List<PointsEntry> ledgerFor(String facultyId) {
        return points.entriesFor(facultyId);
    }

    /** Every professor's balance, for the administrator's overview. */
    public Map<String, PointsBalance> allBalances() {
        Map<String, PointsBalance> balances = new LinkedHashMap<>();
        if (!workspace.hasData()) {
            return balances;
        }
        for (Faculty member : workspace.data().faculty()) {
            balances.put(member.getId(), balanceOf(member.getId()));
        }
        return balances;
    }

    public List<SubstitutionRequest> openOffers() {
        return substitutions.withStatus(SubstitutionStatus.OFFERED);
    }

    public List<SubstitutionRequest> awaitingApproval() {
        return substitutions.withStatus(SubstitutionStatus.CLAIMED);
    }

    public List<SubstitutionRequest> awaitingCompletion() {
        return substitutions.withStatus(SubstitutionStatus.APPROVED);
    }

    public List<SubstitutionRequest> allSubstitutions() {
        return substitutions.all();
    }

    public List<SubstitutionRequest> substitutionsFor(String facultyId) {
        return facultyId == null ? List.of() : substitutions.involving(facultyId);
    }

    public List<DutyRedemption> redemptionsAwaiting() {
        return redemptions.awaitingDecision();
    }

    public List<DutyRedemption> allRedemptions() {
        return redemptions.all();
    }

    public List<DutyRedemption> redemptionsFor(String facultyId) {
        return facultyId == null ? List.of() : redemptions.forFaculty(facultyId);
    }

    /** Closes off offers whose sitting has passed, and writes off credits that lapsed. */
    @Transactional
    public void tidy(String runBy) {
        LocalDateTime now = LocalDateTime.now();
        substitutions.expirePast(now);
        points.expireLapsed(now, runBy);
    }

    private DutyRelief relief() {
        return new DutyRelief(workspace.eligibility(), rules);
    }

    private List<ExamSlot> slotsOf(ExamPlan plan, String facultyId) {
        List<ExamSlot> slots = new ArrayList<>();
        plan.duties().forFaculty(facultyId).forEach(duty -> {
            ExamSlot slot = plan.slot(duty.slotId());
            if (slot != null) {
                slots.add(slot);
            }
        });
        return slots;
    }

    private void requireDecider(Actor actor, SubstitutionRequest request) {
        if (!actor.administrator()) {
            throw new IllegalArgumentException("Only an administrator may decide a substitution");
        }
        if (!request.mayBeDecidedBy(actor.email(), actor.facultyId())) {
            throw new IllegalArgumentException(request.status().mayDecide()
                    ? "You cannot decide a substitution you are party to"
                    : "That offer is not awaiting approval");
        }
    }

    private SubstitutionRequest require(long id) {
        return substitutions.find(id)
                .orElseThrow(() -> new IllegalArgumentException("No substitution with id " + id));
    }

    private DutyRedemption requireRedemption(long id) {
        return redemptions.find(id)
                .orElseThrow(() -> new IllegalArgumentException("No exemption request with id " + id));
    }

    private ExamPlan requirePlan() {
        if (!workspace.hasPlan()) {
            throw new IllegalArgumentException("Import data and compute a plan first");
        }
        return workspace.result().plan();
    }
}
