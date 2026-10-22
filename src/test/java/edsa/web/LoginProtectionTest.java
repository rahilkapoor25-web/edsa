package edsa.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.auth.AccountService;
import edsa.auth.LoginAttempts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Guessing a password is slowed down, and an activation link cannot leak its secret.
 *
 * <p>The throttle's own behaviour is unit-tested in {@code LoginAttemptsTest}; what matters
 * here is that it is actually wired into the filter chain, that it does not get in a
 * legitimate user's way, and that the message it shows still gives nothing away.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginProtectionTest {

    private static final String EMAIL = "coordinator@thapar.edu";
    private static final String PASSWORD = "the-correct-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private LoginAttempts attempts;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void oneAccountAndNoTally() {
        jdbc.update("DELETE FROM app_user");
        accounts.register(EMAIL, PASSWORD, "The Coordinator", "ADMIN");
        attempts.recordSuccess(EMAIL);
        attempts.recordSuccess("ip:127.0.0.1");
    }

    private void failOnce() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").passwordParam("password")
                .user(EMAIL).password("wrong-password"));
    }

    @Test
    void theRightPasswordStillSignsIn() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").passwordParam("password")
                        .user(EMAIL).password(PASSWORD))
                .andExpect(authenticated().withUsername(EMAIL))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void aWrongPasswordStillFailsTheSameWayAsBefore() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").passwordParam("password")
                        .user(EMAIL).password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void aFewMistakesDoNotGetInTheWay() throws Exception {
        failOnce();
        failOnce();

        mvc.perform(formLogin("/login").userParameter("email").passwordParam("password")
                        .user(EMAIL).password(PASSWORD))
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    void aLongRunOfGuessesIsMadeToWait() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            failOnce();
        }

        assertTrue(attempts.isPaused(EMAIL), "the throttle should have started a pause");
        mvc.perform(formLogin("/login").userParameter("email").passwordParam("password")
                        .user(EMAIL).password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?throttled"));
    }

    @Test
    void theGuesserIsStoppedBeforeThePasswordIsEvenChecked() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            failOnce();
        }

        // Even the correct password is refused during the pause, which is the point: the
        // check never runs, so guessing cannot continue at speed.
        mvc.perform(formLogin("/login").userParameter("email").passwordParam("password")
                        .user(EMAIL).password(PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void theWaitingMessageGivesNothingAwayAboutTheAccount() throws Exception {
        mvc.perform(get("/login").param("throttled", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Too many sign-in attempts")))
                // It must not reveal whether the address exists, is disabled, or is unknown.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("does not exist"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("disabled"))));
    }

    @Test
    void anUnknownAddressIsThrottledTheSameAsAKnownOne() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(formLogin("/login").userParameter("email").passwordParam("password")
                    .user("nobody@thapar.edu").password("guess"));
        }

        assertTrue(attempts.isPaused("nobody@thapar.edu"),
                "throttling only known addresses would reveal which addresses exist");
    }

    // ── Activation token exposure ────────────────────────────────────────────────────────

    @Test
    void noPageSendsAReferrerSoAnActivationLinkCannotLeakItsSecret() throws Exception {
        mvc.perform(get("/activate").param("token", "1.some-secret"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void theLoginPageSetsTheSameHeader() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void loggingOutStillWorksAndClearsTheSession() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/logout")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(EMAIL).roles("ADMIN")))
                .andExpect(redirectedUrl("/login?loggedOut"))
                .andExpect(unauthenticated());
    }
}
