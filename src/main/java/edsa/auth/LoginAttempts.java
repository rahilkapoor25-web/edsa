package edsa.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Slows password guessing down without ever locking anybody out for good.
 *
 * <p>After a run of failures a key — the address being tried, and separately the caller it
 * came from — is made to wait. The wait is deliberately short and expires by itself. That is
 * the whole trade-off: a permanent lockout would stop guessing completely, but it would also
 * let anybody disable a colleague's account by typing the wrong password often enough on
 * purpose, which is a worse problem than the one it solves. A minute's pause every ten tries
 * turns an attack of millions of guesses into one that would take years, and costs somebody
 * who genuinely mistyped their password a single minute.
 *
 * <p>Both keys are counted because they answer different attacks: one address being hammered
 * is caught by the address, and one caller working through a list of addresses is caught by
 * the caller.
 *
 * <p>Held in memory, so the tally is forgotten when the application restarts. For one
 * examination office on one server that is the right size; a clustered deployment would need
 * this kept somewhere shared, which is noted rather than built.
 */
@Component
public class LoginAttempts {

    /** What one key has been up to lately. */
    private static final class Tally {
        private int failures;
        private Instant firstFailure;
        private Instant pausedUntil;
    }

    private final Map<String, Tally> tallies = new ConcurrentHashMap<>();
    private final AccountProperties properties;

    public LoginAttempts(AccountProperties properties) {
        this.properties = properties;
    }

    /** Whether this key is in its pause and should not be allowed another try yet. */
    public boolean isPaused(String key) {
        return isPaused(key, Instant.now());
    }

    synchronized boolean isPaused(String key, Instant now) {
        Tally tally = tallies.get(normalise(key));
        return tally != null && tally.pausedUntil != null && now.isBefore(tally.pausedUntil);
    }

    /** Records a failed sign-in, starting a pause once the run is long enough. */
    public void recordFailure(String key) {
        recordFailure(key, Instant.now());
    }

    synchronized void recordFailure(String key, Instant now) {
        forgetStale(now);
        Tally tally = tallies.computeIfAbsent(normalise(key), ignored -> new Tally());
        if (tally.firstFailure == null || outsideWindow(tally.firstFailure, now)) {
            tally.firstFailure = now;
            tally.failures = 0;
        }
        tally.failures++;
        if (tally.failures >= properties.getLoginMaxAttempts()) {
            tally.pausedUntil = now.plusSeconds(properties.getLoginCooldownSeconds());
            tally.failures = 0;
            tally.firstFailure = now;
        }
    }

    /** A correct password clears the tally, so one good sign-in wipes the slate. */
    public synchronized void recordSuccess(String key) {
        tallies.remove(normalise(key));
    }

    /** How long this key must still wait, for the message shown to whoever is waiting. */
    public synchronized Duration remainingPause(String key) {
        Tally tally = tallies.get(normalise(key));
        if (tally == null || tally.pausedUntil == null) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(Instant.now(), tally.pausedUntil);
        return left.isNegative() ? Duration.ZERO : left;
    }

    private boolean outsideWindow(Instant first, Instant now) {
        return first.plusSeconds(properties.getLoginWindowSeconds()).isBefore(now);
    }

    /** Keeps the map from growing without bound on a long-running server. */
    private void forgetStale(Instant now) {
        tallies.entrySet().removeIf(entry -> {
            Tally tally = entry.getValue();
            boolean pausePassed = tally.pausedUntil == null || now.isAfter(tally.pausedUntil);
            boolean failuresForgotten = tally.firstFailure == null || outsideWindow(tally.firstFailure, now);
            return pausePassed && failuresForgotten;
        });
    }

    private static String normalise(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }
}
