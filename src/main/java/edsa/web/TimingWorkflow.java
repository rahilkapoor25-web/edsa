package edsa.web;

import edsa.core.Durations;
import edsa.core.Exam;
import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.core.TimingOverride;
import edsa.core.TimingOverrides;
import edsa.core.TimingReview;
import edsa.core.TimingValidator;
import edsa.data.TimingChange;
import edsa.data.TimingRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changing when papers run, with the review and the audit history that has to go with it.
 *
 * <p>Saving is deliberately two steps. A change is reviewed first — applied to a copy of the
 * data, planned, and compared against the plan as it stands — and the findings are shown before
 * anything is written. A coordinator may then save in spite of warnings, and the warnings they
 * saw are written into the audit history with the change, because "did they know?" is the first
 * question anybody asks about a timetable with a clash in it.
 *
 * <p>Blockers are different and cannot be confirmed past: a length no mode accepts or a sitting
 * that would run after the hall closes is not a trade-off somebody can take responsibility for.
 */
@Service
public class TimingWorkflow {

    private final TimingRepository timings;
    private final Workspace workspace;

    public TimingWorkflow(TimingRepository timings, Workspace workspace) {
        this.timings = timings;
        this.workspace = workspace;
    }

    /** One paper as the timing page shows it: how it was declared and how it now runs. */
    public record PaperTiming(String examId, String paperCode, String paperName,
                              LocalDate date, LocalTime start, int minutes,
                              boolean overridden, String declaredSummary) {

        public String currentSummary() {
            return date + (start == null ? " (scheduler chooses)" : " " + start)
                    + " for " + Durations.label(minutes);
        }
    }

    /** Every declared paper, with the override in force applied. */
    public List<PaperTiming> papers() {
        ExamData data = workspace.data();
        if (data == null) {
            return List.of();
        }
        ExamMode mode = workspace.mode();
        TimingOverrides inForce = workspace.overridesInForce();
        List<PaperTiming> rows = new ArrayList<>();
        for (Exam declared : data.exams()) {
            Exam effective = inForce.find(declared.getId())
                    .map(override -> declared.retimed(override.date(), override.start(), override.minutes()))
                    .orElse(declared);
            rows.add(new PaperTiming(declared.getId(), declared.getSubjectCode(), declared.getSubjectName(),
                    effective.getDate(), effective.getPermittedStart().orElse(null),
                    effective.durationUnder(mode),
                    inForce.find(declared.getId()).isPresent(),
                    summaryOf(declared, mode)));
        }
        return rows;
    }

    private String summaryOf(Exam exam, ExamMode mode) {
        return exam.getDate() + (exam.getPermittedStart().map(start -> " " + start).orElse(" (scheduler chooses)"))
                + " for " + Durations.label(exam.durationUnder(mode));
    }

    /** What this change would do, without saving anything. */
    public TimingReview review(TimingOverride proposal) {
        ExamData data = workspace.data();
        if (data == null) {
            return TimingReview.blocked("Import data before changing any timing");
        }
        TimingValidator validator = new TimingValidator(workspace.planner(), workspace.config());
        return validator.review(data, workspace.mode(), workspace.overridesInForce(),
                proposal, workspace.result());
    }

    /**
     * Saves one paper's new timing.
     *
     * <p>Reviewed again here rather than trusting the review the page already did. The data may
     * have been reimported, the mode changed or another paper moved between the two requests, so
     * a blocker found now is a blocker whatever the page was showing a minute ago.
     *
     * @param acceptWarnings whether the coordinator has confirmed the warnings they were shown
     * @return the review that was acted on, so the page can report what was accepted
     */
    @Transactional
    public TimingReview save(TimingOverride proposal, String changedBy, boolean acceptWarnings) {
        TimingReview review = review(proposal);
        if (review.isBlocked()) {
            return review;
        }
        if (review.hasWarnings() && !acceptWarnings) {
            return review;
        }

        PaperTiming before = papers().stream()
                .filter(paper -> paper.examId().equals(proposal.examId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No paper with id " + proposal.examId()));

        timings.save(proposal, changedBy);
        workspace.compute();

        PaperTiming after = papers().stream()
                .filter(paper -> paper.examId().equals(proposal.examId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the paper vanished while being re-timed"));

        timings.record(TimingChange.of(proposal.examId(), before.paperCode(),
                before.currentSummary(), after.currentSummary(), "PAPER", changedBy,
                review.hasWarnings() ? String.join("; ", review.warnings()) : null));
        return review;
    }

    /**
     * Moves every paper on one date, for a session-wide change.
     *
     * <p>The whole move is one transaction and is refused outright if any paper in it is blocked,
     * rather than moving the papers that fit and leaving the rest behind. A half-moved session is
     * worse than an unmoved one: nobody can tell by looking which papers were meant to move.
     */
    @Transactional
    public SessionShift shiftSession(LocalDate date, LocalTime newStart, Integer newMinutes,
                                     String changedBy, boolean acceptWarnings) {
        List<PaperTiming> onThatDate = papers().stream()
                .filter(paper -> paper.date().equals(date))
                .toList();
        if (onThatDate.isEmpty()) {
            return new SessionShift(0, List.of("No paper is scheduled on " + date), List.of());
        }

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (PaperTiming paper : onThatDate) {
            TimingReview review = review(new TimingOverride(paper.examId(), null, newStart, newMinutes));
            review.blockers().forEach(blocker -> blockers.add(paper.paperCode() + ": " + blocker));
            review.warnings().forEach(warning -> warnings.add(paper.paperCode() + ": " + warning));
        }
        if (!blockers.isEmpty() || (!warnings.isEmpty() && !acceptWarnings)) {
            return new SessionShift(0, blockers, warnings);
        }

        for (PaperTiming paper : onThatDate) {
            TimingOverride override = new TimingOverride(paper.examId(), null, newStart, newMinutes);
            timings.save(override, changedBy);
        }
        workspace.compute();

        String note = warnings.isEmpty() ? null : String.join("; ", warnings);
        for (PaperTiming paper : onThatDate) {
            PaperTiming after = papers().stream()
                    .filter(row -> row.examId().equals(paper.examId()))
                    .findFirst()
                    .orElse(paper);
            timings.record(TimingChange.of(paper.examId(), paper.paperCode(),
                    paper.currentSummary(), after.currentSummary(), "SESSION", changedBy, note));
        }
        return new SessionShift(onThatDate.size(), List.of(), warnings);
    }

    /** What a session-wide move did, or why it did nothing. */
    public record SessionShift(int moved, List<String> blockers, List<String> warnings) {

        public boolean succeeded() {
            return moved > 0;
        }
    }

    /** Puts one paper back on its declared timing. The history of the change stays. */
    @Transactional
    public void revert(String examId, String changedBy) {
        PaperTiming before = papers().stream()
                .filter(paper -> paper.examId().equals(examId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No paper with id " + examId));
        timings.clear(examId);
        workspace.compute();
        PaperTiming after = papers().stream()
                .filter(paper -> paper.examId().equals(examId))
                .findFirst()
                .orElse(before);
        timings.record(TimingChange.of(examId, before.paperCode(), before.currentSummary(),
                after.currentSummary() + " (as declared)", "PAPER", changedBy, null));
    }

    public List<TimingChange> history(int limit) {
        return timings.history(limit);
    }
}
