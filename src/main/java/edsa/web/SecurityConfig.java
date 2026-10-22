package edsa.web;

import edsa.auth.AccountStatusFilter;
import edsa.auth.DatabaseUserDetailsService;
import edsa.auth.LoginAttempts;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Real authentication: accounts come from the database and passwords are checked against a
 * BCrypt hash.
 *
 * <p>Everything is protected except the login page and the stylesheet. CSRF protection is on
 * for every state-changing request, the session id is replaced on login so a fixed session
 * cannot be reused, and logging out invalidates the session and clears its cookie, which is
 * what stops the browser's back button reaching a protected page afterwards.
 *
 * <p>A run of failed sign-ins earns a short pause rather than a lockout
 * ({@link LoginAttempts}), and no page sends a Referer header, so an activation link cannot
 * leak its secret to the font service the stylesheet comes from.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(DatabaseUserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users);
        provider.setPasswordEncoder(encoder);
        // Always run the hash comparison, so a missing account takes as long as a wrong password.
        provider.setHideUserNotFoundExceptions(true);
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    /** The address a sign-in was attempted for, as the throttle keys it. */
    private static String attemptedEmail(HttpServletRequest request) {
        String email = request.getParameter("email");
        return email == null ? "" : email;
    }

    /** The caller, so one machine working through a list of addresses is also slowed. */
    private static String callerOf(HttpServletRequest request) {
        return "ip:" + request.getRemoteAddr();
    }

    /**
     * Turns a run of failed sign-ins into a short wait.
     *
     * <p>Sits in front of the login filter so a paused caller never reaches the password
     * check at all, which is what makes the pause worth anything.
     */
    private OncePerRequestFilter loginPauseFilter(LoginAttempts attempts) {
        // The same matcher the login filter itself uses, so "is this a sign-in attempt?" is
        // answered identically here and there.
        AntPathRequestMatcher signIn = new AntPathRequestMatcher("/login", "POST");
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                if (signIn.matches(request)
                        && (attempts.isPaused(attemptedEmail(request)) || attempts.isPaused(callerOf(request)))) {
                    response.sendRedirect(request.getContextPath() + "/login?throttled");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }

    private AuthenticationFailureHandler countingFailureHandler(LoginAttempts attempts) {
        return (request, response, exception) -> {
            attempts.recordFailure(attemptedEmail(request));
            attempts.recordFailure(callerOf(request));
            new SimpleUrlAuthenticationFailureHandler("/login?error")
                    .onAuthenticationFailure(request, response, exception);
        };
    }

    private AuthenticationSuccessHandler clearingSuccessHandler(LoginAttempts attempts) {
        SavedRequestAwareAuthenticationSuccessHandler delegate =
                new SavedRequestAwareAuthenticationSuccessHandler();
        delegate.setDefaultTargetUrl("/");
        delegate.setAlwaysUseDefaultTargetUrl(true);
        return (request, response, authentication) -> {
            attempts.recordSuccess(attemptedEmail(request));
            attempts.recordSuccess(callerOf(request));
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager,
                                           LoginAttempts attempts, AccountStatusFilter accountStatus)
            throws Exception {
        http
                .addFilterBefore(loginPauseFilter(attempts), UsernamePasswordAuthenticationFilter.class)
                // After the session's authentication has been restored and before anything is
                // authorised with it, so a disabled account is turned away rather than let
                // through on what its session still remembers.
                .addFilterBefore(accountStatus, AuthorizationFilter.class)
                .authenticationManager(authenticationManager)
                .authorizeHttpRequests(requests -> requests
                        // /activate is open by design: an account awaiting activation cannot
                        // sign in, so its owner has no other way to set a password. The
                        // link's random single-use secret is the credential.
                        .requestMatchers("/login", "/activate", "/css/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/users/**", "/users").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(clearingSuccessHandler(attempts))
                        .failureHandler(countingFailureHandler(attempts))
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?loggedOut")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.newSession()))
                .headers(headers -> headers
                        .cacheControl(cache -> {
                        })
                        // The activation link carries its secret in the query string, and every
                        // page loads a stylesheet from a font service. Without this the browser
                        // would hand that whole URL to the font service in the Referer header.
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }
}
