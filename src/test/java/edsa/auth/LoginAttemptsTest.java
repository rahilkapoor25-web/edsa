package edsa.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * The guessing throttle, driven by an explicit clock so no test has to sleep.
 *
 * <p>The behaviour that matters most is the last two tests: the pause ends on its own, and a
 * correct password clears the tally. Those are what stop this being a lockout.
 */
class LoginAttemptsTest {

    private static final Instant START = Instant.parse("2026-11-10T09:00:00Z");

    private static AccountProperties properties(int maxAttempts, int cooldownSeconds, int windowSeconds) {
        AccountProperties properties = new AccountProperties();
        properties.setLoginMaxAttempts(maxAttempts);
        properties.setLoginCooldownSeconds(cooldownSeconds);
        properties.setLoginWindowSeconds(windowSeconds);
        return properties;
    }

    private static LoginAttempts throttle() {
        return new LoginAttempts(properties(3, 60, 300));
    }

    @Test
    void anHonestMistakeIsNotPunished() {
        LoginAttempts attempts = throttle();

        attempts.recordFailure("someone@thapar.edu", START);
        attempts.recordFailure("someone@thapar.edu", START.plusSeconds(2));

        assertFalse(attempts.isPaused("someone@thapar.edu", START.plusSeconds(3)),
                "two slips is not an attack");
    }

    @Test
    void aRunOfFailuresEarnsAPause() {
        LoginAttempts attempts = throttle();

        for (int attempt = 0; attempt < 3; attempt++) {
            attempts.recordFailure("someone@thapar.edu", START.plusSeconds(attempt));
        }

        assertTrue(attempts.isPaused("someone@thapar.edu", START.plusSeconds(4)));
    }

    @Test
    void thePauseEndsByItselfSoNobodyIsLockedOut() {
        LoginAttempts attempts = throttle();
        for (int attempt = 0; attempt < 3; attempt++) {
            attempts.recordFailure("someone@thapar.edu", START.plusSeconds(attempt));
        }

        assertTrue(attempts.isPaused("someone@thapar.edu", START.plusSeconds(30)));
        assertFalse(attempts.isPaused("someone@thapar.edu", START.plusSeconds(120)),
                "a permanent lockout would let anybody disable a colleague's account");
    }

    @Test
    void aCorrectPasswordClearsTheTally() {
        LoginAttempts attempts = throttle();
        for (int attempt = 0; attempt < 3; attempt++) {
            attempts.recordFailure("someone@thapar.edu", START.plusSeconds(attempt));
        }

        attempts.recordSuccess("someone@thapar.edu");

        assertFalse(attempts.isPaused("someone@thapar.edu", START.plusSeconds(5)));
    }

    @Test
    void failuresSpreadOutAreForgottenRatherThanAccumulating() {
        LoginAttempts attempts = throttle();

        // One failure every ten minutes, well outside the five-minute window.
        attempts.recordFailure("someone@thapar.edu", START);
        attempts.recordFailure("someone@thapar.edu", START.plusSeconds(600));
        attempts.recordFailure("someone@thapar.edu", START.plusSeconds(1200));

        assertFalse(attempts.isPaused("someone@thapar.edu", START.plusSeconds(1201)),
                "somebody who mistypes once a week should never be paused");
    }

    @Test
    void onePausedAddressDoesNotPauseAnother() {
        LoginAttempts attempts = throttle();
        for (int attempt = 0; attempt < 3; attempt++) {
            attempts.recordFailure("target@thapar.edu", START.plusSeconds(attempt));
        }

        assertTrue(attempts.isPaused("target@thapar.edu", START.plusSeconds(4)));
        assertFalse(attempts.isPaused("somebody.else@thapar.edu", START.plusSeconds(4)));
    }

    @Test
    void theAddressIsMatchedWithoutRegardToCaseOrSpacing() {
        LoginAttempts attempts = throttle();
        for (int attempt = 0; attempt < 3; attempt++) {
            attempts.recordFailure("Someone@Thapar.edu", START.plusSeconds(attempt));
        }

        assertTrue(attempts.isPaused("  someone@thapar.edu  ", START.plusSeconds(4)),
                "changing the capitalisation must not reset the tally");
    }

    @Test
    void theRemainingPauseIsReportedForTheMessage() {
        LoginAttempts attempts = throttle();
        for (int attempt = 0; attempt < 3; attempt++) {
            attempts.recordFailure("someone@thapar.edu", Instant.now());
        }

        assertTrue(attempts.remainingPause("someone@thapar.edu").toSeconds() > 0);
        assertTrue(attempts.remainingPause("never-failed@thapar.edu").isZero());
    }
}
