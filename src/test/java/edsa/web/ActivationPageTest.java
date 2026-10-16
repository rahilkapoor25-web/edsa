package edsa.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.auth.ProfessorAccountService;
import edsa.auth.ProvisionedAccount;
import edsa.auth.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A professor whose account was created for them, setting their own password.
 *
 * <p>The activation page has to be reachable without signing in, so these tests are mostly
 * about what that openness does and does not allow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivationPageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProfessorAccountService professors;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void oneImportedProfessor() {
        jdbc.update("DELETE FROM app_user");
        jdbc.update("DELETE FROM faculty");
        jdbc.update("INSERT INTO faculty (id, name, email, department, available) "
                + "VALUES ('F01', 'Dr Ayaan Example', 'ayaan@example.edu', 'CSE', TRUE)");
    }

    private String aFreshToken() {
        return professors.createMissingAccounts().created().stream()
                .map(ProvisionedAccount::activationToken)
                .findFirst().orElseThrow();
    }

    @Test
    void theActivationPageIsReachableWithoutSigningIn() throws Exception {
        mvc.perform(get("/activate").param("token", aFreshToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Set your password")))
                .andExpect(unauthenticated());
    }

    @Test
    void settingAPasswordLetsThemSignInAfterwards() throws Exception {
        String token = aFreshToken();

        mvc.perform(post("/activate").with(csrf())
                        .param("token", token)
                        .param("password", "a-chosen-password")
                        .param("confirmation", "a-chosen-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your password is set")));

        assertTrue(users.findByEmail("ayaan@example.edu").orElseThrow().active());
        mvc.perform(SecurityMockMvcRequestBuilders.formLogin("/login")
                        .userParameter("email").passwordParam("password")
                        .user("ayaan@example.edu").password("a-chosen-password"))
                .andExpect(authenticated().withUsername("ayaan@example.edu"));
    }

    @Test
    void theAccountCannotBeSignedIntoBeforeActivation() throws Exception {
        aFreshToken();

        mvc.perform(SecurityMockMvcRequestBuilders.formLogin("/login")
                        .userParameter("email").passwordParam("password")
                        .user("ayaan@example.edu").password("a-chosen-password"))
                .andExpect(unauthenticated());
    }

    @Test
    void mismatchedPasswordsAreRefusedAndTheLinkStillWorks() throws Exception {
        String token = aFreshToken();

        mvc.perform(post("/activate").with(csrf())
                        .param("token", token)
                        .param("password", "a-chosen-password")
                        .param("confirmation", "a-different-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("do not match")));

        assertFalse(users.findByEmail("ayaan@example.edu").orElseThrow().active());
        assertTrue(professors.isActivatable(token), "the link is not burnt by a mistake");
    }

    @Test
    void aShortPasswordIsRefused() throws Exception {
        String token = aFreshToken();

        mvc.perform(post("/activate").with(csrf())
                        .param("token", token).param("password", "short").param("confirmation", "short"))
                .andExpect(content().string(containsString("at least 8 characters")));

        assertFalse(users.findByEmail("ayaan@example.edu").orElseThrow().active());
    }

    @Test
    void anInvalidTokenShowsNoPasswordFormAtAll() throws Exception {
        aFreshToken();

        mvc.perform(get("/activate").param("token", "1.not-the-secret"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("not valid")))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    void aMissingTokenShowsNoPasswordFormEither() throws Exception {
        mvc.perform(get("/activate"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    void anInvalidTokenCannotSetAnybodysPassword() throws Exception {
        aFreshToken();

        mvc.perform(post("/activate").with(csrf())
                        .param("token", "1.not-the-secret")
                        .param("password", "a-chosen-password")
                        .param("confirmation", "a-chosen-password"))
                .andExpect(content().string(containsString("not valid")));

        assertFalse(users.findByEmail("ayaan@example.edu").orElseThrow().active());
    }

    @Test
    void activatingNeedsACsrfToken() throws Exception {
        String token = aFreshToken();

        mvc.perform(post("/activate")
                        .param("token", token)
                        .param("password", "a-chosen-password")
                        .param("confirmation", "a-chosen-password"))
                .andExpect(status().isForbidden());

        assertFalse(users.findByEmail("ayaan@example.edu").orElseThrow().active());
    }

    @Test
    void theOpenActivationPageDoesNotOpenAnythingElse() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/users")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/disruption")).andExpect(status().is3xxRedirection());
    }
}
