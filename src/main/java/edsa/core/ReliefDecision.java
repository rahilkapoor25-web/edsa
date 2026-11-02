package edsa.core;

import java.util.Optional;

/**
 * Whether a duty may be given up for points, and who would take it instead.
 *
 * <p>A refusal always carries its reason, because the reasons are not interchangeable: "you
 * cannot afford it" is the professor's problem, "nobody else is free" is the examination
 * office's, and "it would take you below your minimum" is a policy somebody set. Telling
 * somebody only that it was refused invites them to ask again tomorrow.
 *
 * @param replacementFacultyId the colleague who would take the duty, when one was found
 */
public record ReliefDecision(boolean allowed, String reason, String replacementFacultyId) {

    public static ReliefDecision refused(String reason) {
        return new ReliefDecision(false, reason, null);
    }

    public static ReliefDecision allowedWith(String replacementFacultyId) {
        return new ReliefDecision(true, "A colleague is free to take it", replacementFacultyId);
    }

    public Optional<String> replacement() {
        return Optional.ofNullable(replacementFacultyId);
    }
}
