package edsa.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.auth.AccountService;

import org.hamcrest.Matchers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Switching an account off stops it being used now, not at its owner's next sign-in.
 *
 * <p>The session is where this is easy to get wrong: the account is read from the database
 * once, at sign-in, so without a per-request check somebody already signed in would keep
 * working for as long as they stayed signed in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisabledAccountTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void oneProfessor() {
        jdbc.update("DELETE FROM app_user");
        accounts.register("professor@thapar.edu", "professor-pass-1", "A Professor", "PROFESSOR");
    }

    /** Signs in and returns the session that sign-in produced. */
    private MockHttpSession signedIn() throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login")
                        .userParameter("email").passwordParam("password")
                        .user("professor@thapar.edu").password("professor-pass-1"))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession();
    }

    @Test
    void anOpenSessionStopsWorkingAsSoonAsTheAccountIsSwitchedOff() throws Exception {
        MockHttpSession session = signedIn();
        mvc.perform(get("/rooms").session(session)).andExpect(status().isOk());

        accounts.setActive("professor@thapar.edu", false);

        mvc.perform(get("/rooms").session(session)).andExpect(redirectedUrl("/login?disabled"));
        assertTrue(session.isInvalid(), "the session itself should be gone, not just its authentication");
    }

    @Test
    void theSwitchedOffOwnerIsToldWhyRatherThanLeftGuessing() throws Exception {
        // An empty value is what the container gives for the bare "?disabled" the filter
        // redirects to; MockMvc does not bind a valueless query parameter by itself.
        mvc.perform(get("/login").param("disabled", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("switched off")));
    }

    @Test
    void aSwitchedOffAccountCannotSignInAgainEvenWithTheRightPassword() throws Exception {
        accounts.setActive("professor@thapar.edu", false);

        mvc.perform(formLogin("/login")
                        .userParameter("email").passwordParam("password")
                        .user("professor@thapar.edu").password("professor-pass-1"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void switchingTheAccountBackOnLetsItSignInWithTheSamePassword() throws Exception {
        accounts.setActive("professor@thapar.edu", false);
        accounts.setActive("professor@thapar.edu", true);

        MockHttpSession session = signedIn();
        mvc.perform(get("/rooms").session(session)).andExpect(status().isOk());
        assertFalse(session.isInvalid());
    }

    @Test
    void anAccountStillActiveIsLeftAlone() throws Exception {
        MockHttpSession session = signedIn();
        for (int request = 0; request < 3; request++) {
            mvc.perform(get("/rooms").session(session)).andExpect(status().isOk());
        }
        assertFalse(session.isInvalid());
    }
}
