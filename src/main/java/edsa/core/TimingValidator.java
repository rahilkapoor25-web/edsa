package edsa.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks a proposed timing change by making it and looking at what happens.
 *
 * <p>This deliberately does not reimplement clash detection. The scheduler already knows how to
 * find a student sitting two papers at once, a room with nowhere to put a cohort and a session
 * with more rooms running than invigilators to staff them, and the rule checker already knows
 * how to prove a finished plan legal. Writing a second, parallel set of those checks here would
 * be the sort of duplication that drifts: the warning page would eventually disagree with the
 * plan it was warning about. So the proposal is applied to a copy of the data, the whole thing
 * is planned again, and the findings are whatever the real engine says is newly wrong.
 *
 * <p>That is why the warnings are a difference rather than a list. A session with a paper
 * nobody can staff keeps that problem whatever else changes, and reporting it as though the
 * coordinator had just caused it would train them to ignore the warnings. Only what the change
 * itself adds is shown.
 */
public final class TimingValidator {

    private final ExamPlanner planner;
    private final SchedulingConfig config;

    public TimingValidator(ExamPlanner planner, SchedulingConfig config) {
        this.planner = planner;
        this.config = config;
    }

    /**
     * What saving this override would do.
     *
     * @param data      the imported data, with the overrides already in force applied
     * @param mode      the mode the session is being planned under
     * @param inForce   the overrides already saved
     * @param proposal  the change being considered
     * @param current   the plan as it stands, to compare against; may be null before one exists
     */
    public TimingReview review(ExamData data, ExamMode mode, TimingOverrides inForce,
                               TimingOverride proposal, PlanningResult current) {
        List<String> blockers = structuralBlockers(data, mode, proposal);
        if (!blockers.isEmpty()) {
            return new TimingReview(blockers, List.of(), null);
        }

        ExamData retimed = inForce.with(proposal).applyTo(data);
        PlanningResult after = planner.plan(retimed, mode);

        List<String> warnings = new ArrayList<>(newFindings(problemsOf(current), problemsOf(after)));
        warnings.addAll(newFindings(breachesOf(current), breachesOf(after)));
        return new TimingReview(List.of(), warnings, after);
    }

    /**
     * The checks that do not need a plan: a paper that does not exist, a length no mode will
     * accept, a sitting that cannot fit inside the examination day.
     */
    private List<String> structuralBlockers(ExamData data, ExamMode mode, TimingOverride proposal) {
        List<String> blockers = new ArrayList<>();
        Exam exam = data == null ? null : data.exam(proposal.examId());
        if (exam == null) {
            blockers.add("There is no paper with id " + proposal.examId() + " in the imported data");
            return blockers;
        }
        if (!proposal.changesAnything()) {
            blockers.add("Nothing was changed");
            return blockers;
        }
        if (proposal.minutes() != null && !mode.permitsApproved(proposal.minutes())) {
            blockers.add("A sitting must run between " + ExamMode.MIN_APPROVED_MINUTES + " and "
                    + ExamMode.MAX_OVERRIDE_MINUTES + " minutes; " + proposal.minutes() + " is outside that");
        }

        int minutes = proposal.minutes() == null
                ? exam.durationUnder(mode)
                : mode.minutesForApproved(proposal.minutes());
        if (proposal.start() != null) {
            if (proposal.start().isBefore(config.dayStart())) {
                blockers.add("It would start at " + proposal.start() + ", before the hall opens at "
                        + config.dayStart());
            }
            // Compared on the clock rather than by adding to a date, so a sitting pushed past
            // midnight is caught here instead of appearing to finish early the next morning.
            long endMinute = proposal.start().toSecondOfDay() / 60L + minutes;
            long closeMinute = config.dayEnd().toSecondOfDay() / 60L;
            if (endMinute > closeMinute) {
                blockers.add("It would finish at " + Durations.clock(endMinute) + ", after the hall closes at "
                        + config.dayEnd());
            }
        }
        return blockers;
    }

    private static List<String> problemsOf(PlanningResult result) {
        if (result == null) {
            return List.of();
        }
        List<String> all = new ArrayList<>(result.schedulingProblems());
        all.addAll(result.allocationProblems());
        return all;
    }

    private static List<String> breachesOf(PlanningResult result) {
        if (result == null) {
            return List.of();
        }
        List<String> breaches = new ArrayList<>();
        result.checks().forEach(check -> breaches.addAll(check.breaches()));
        return breaches;
    }

    /**
     * What is wrong after that was not wrong before.
     *
     * <p>Counted rather than set-subtracted: the same sentence can legitimately appear twice —
     * two rooms unstaffed for the same sitting read identically — and dropping the duplicate
     * would hide the second one.
     *
     * <p>Compared on a key with the times and dates taken out, because most findings quote the
     * time they are about. A paper that could not be seated at 14:00 and still cannot be seated
     * at 15:00 has the same problem it always had, but the two sentences differ, so a literal
     * comparison would report it as freshly caused by the change — which is precisely the
     * false alarm this method exists to prevent.
     */
    private static List<String> newFindings(List<String> before, List<String> after) {
        List<String> remaining = new ArrayList<>();
        before.forEach(finding -> remaining.add(key(finding)));
        List<String> added = new ArrayList<>();
        for (String finding : after) {
            if (!remaining.remove(key(finding))) {
                added.add(finding);
            }
        }
        return added;
    }

    /** A finding stripped of the clock times and dates it quotes, for comparing two plans. */
    private static String key(String finding) {
        return finding
                .replaceAll("\\d{4}-\\d{2}-\\d{2}", "<date>")
                .replaceAll("\\d{1,2}:\\d{2}", "<time>");
    }
}
