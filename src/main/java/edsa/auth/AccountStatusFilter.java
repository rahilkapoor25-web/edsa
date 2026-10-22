package edsa.auth;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Makes disabling an account take effect at once, including for whoever is using it.
 *
 * <p>Spring Security reads the account from the database when somebody signs in and then
 * keeps the result in their session. Without this filter, switching an account off would only
 * stop the <em>next</em> sign-in: a professor already looking at the plan would carry on until
 * they happened to log out, which is not what an administrator clicking "Disable" means. The
 * reason to disable somebody is usually that they should stop now.
 *
 * <p>So every request from a signed-in account asks the database whether that account is still
 * there and still active, and ends the session the moment it is not. That costs one indexed
 * lookup per request, which is far less than rendering the page it precedes, and it holds
 * however the account was switched off — through the Accounts page, by another instance, or
 * directly in the database.
 */
@Component
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public AccountStatusFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (revoked(SecurityContextHolder.getContext().getAuthentication())) {
            signOut(request);
            response.sendRedirect(request.getContextPath() + "/login?disabled");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Whether this request's account has since been switched off. */
    private boolean revoked(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        Optional<AppUser> account = users.findByEmail(authentication.getName());
        return account.isPresent() && !account.get().active();
    }

    /**
     * Drops the session rather than only the authentication, so nothing the signed-out account
     * put there — the loaded workbook, the computed plan — is left for the next person on that
     * browser.
     */
    private void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
