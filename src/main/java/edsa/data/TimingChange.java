package edsa.data;

import java.time.LocalDateTime;

/**
 * One entry in the timing audit history: what was changed, by whom, and what it broke.
 *
 * <p>The warnings are kept with the change on purpose. Whether a clash was created knowingly is
 * the first thing anybody reviewing a timetable afterwards wants to know, and it cannot be
 * reconstructed later — the plan has moved on and the warning no longer exists to be found.
 *
 * @param was      how the paper was timed before
 * @param became   how it is timed now
 * @param scope    PAPER for one paper, SESSION for a change applied across the session
 * @param warnings what the page was showing when it was saved, or null when it was clean
 */
public record TimingChange(long id, String examId, String paperCode, String was, String became,
                           String scope, String changedBy, LocalDateTime changedAt, String warnings) {

    public static TimingChange of(String examId, String paperCode, String was, String became,
                                  String scope, String changedBy, String warnings) {
        return new TimingChange(0, examId, paperCode, was, became, scope, changedBy,
                LocalDateTime.now(), warnings);
    }

    public boolean wasSavedWithWarnings() {
        return warnings != null && !warnings.isBlank();
    }
}
