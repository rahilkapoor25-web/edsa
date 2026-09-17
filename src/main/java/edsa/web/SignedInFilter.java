package edsa.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Sends anyone without a session to the login page.
 *
 * <p>This is a placeholder gate rather than security: it only checks that somebody typed an
 * institute address into {@link LoginController}, which verifies nothing. See the SRS, where
 * the missing security layer is recorded as a scope limitation.
 */
@Component
public class SignedInFilter extends HttpFilter {

    private static final Set<String> OPEN_PATHS = Set.of("/login", "/logout");

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (isOpen(request.getRequestURI()) || isSignedIn(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private static boolean isOpen(String path) {
        return OPEN_PATHS.contains(path) || path.startsWith("/css/") || path.equals("/favicon.ico");
    }

    private static boolean isSignedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(LoginController.USER_EMAIL) != null;
    }
}
