package edsa.web;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Who is signed in, in the terms the workflows need: an address, a role, and the faculty record
 * the account belongs to.
 *
 * <p>The faculty id is the part worth being careful about, and the reason this exists rather
 * than each controller working it out. Every "is this your own duty?", "may you decide this
 * case?" and "whose points are these?" turns on it, so it is read from the account's stored
 * mapping and never from a parameter in the request — a form field saying which professor you
 * are would be a way of being somebody else.
 */
@Component
public class SignedInActor {

    private final LeaveWorkflow leave;

    public SignedInActor(LeaveWorkflow leave) {
        this.leave = leave;
    }

    public boolean isAdministrator(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    /**
     * Whether this account may change examination timings and approve duty allocations.
     *
     * <p>An administrator may: they can already do everything else. A coordinator is the role
     * that exists for exactly this and nothing more, so an office can let somebody run the
     * timetable without also letting them create accounts.
     */
    public boolean isCoordinator(Authentication authentication) {
        return isAdministrator(authentication) || hasRole(authentication, "ROLE_COORDINATOR");
    }

    public String emailOf(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }

    /** The faculty record this account was provisioned from, when it was provisioned from one. */
    public Optional<String> facultyIdOf(Authentication authentication) {
        String email = emailOf(authentication);
        return email == null ? Optional.empty() : leave.linkedFacultyId(email);
    }

    public RewardWorkflow.Actor rewardActor(Authentication authentication) {
        return new RewardWorkflow.Actor(emailOf(authentication),
                facultyIdOf(authentication).orElse(null),
                isAdministrator(authentication));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
