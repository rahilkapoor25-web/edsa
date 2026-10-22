package edsa.auth;

import java.time.LocalDateTime;

/**
 * The unfinished part of a provisioned account: the hash of its activation secret and when
 * that secret stops working.
 *
 * <p>Read only while verifying an activation link. The secret itself is not here and is not
 * anywhere — only its hash was ever stored.
 */
record PendingActivation(long accountId, String email, String displayName,
                         String activationHash, LocalDateTime expiresAt) {

    boolean hasExpired(LocalDateTime now) {
        return expiresAt == null || now.isAfter(expiresAt);
    }

    boolean isUsable(LocalDateTime now) {
        return activationHash != null && !hasExpired(now);
    }
}
