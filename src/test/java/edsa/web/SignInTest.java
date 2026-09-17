package edsa.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SignInTest {

    private static final String USER_EMAIL = "userEmail";

    @Autowired
    private MockMvc mvc;

    private static MockHttpSession signedInSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(USER_EMAIL, "rk@thapar.edu");
        return session;
    }

    @Test
    void sendsAStrangerToTheLoginPage() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void letsTheStylesheetThroughWithoutASession() throws Exception {
        mvc.perform(get("/css/edsa.css")).andExpect(status().isOk());
    }

    @Test
    void acceptsAnInstituteAddressAndRemembersIt() throws Exception {
        HttpSession session = mvc.perform(post("/login")
                        .param("email", "RK@Thapar.edu")
                        .param("password", "anything"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getRequest()
                .getSession(false);

        assertEquals("rk@thapar.edu", session.getAttribute(USER_EMAIL));
    }

    @Test
    void refusesAnyOtherDomainWithAnInlineError() throws Exception {
        mvc.perform(post("/login").param("email", "someone@gmail.com").param("password", "anything"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Use your @thapar.edu account")))
                .andExpect(request().sessionAttributeDoesNotExist(USER_EMAIL));
    }

    @Test
    void refusesAnEmptyPassword() throws Exception {
        mvc.perform(post("/login").param("email", "rk@thapar.edu").param("password", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter your password")))
                .andExpect(request().sessionAttributeDoesNotExist(USER_EMAIL));
    }

    @Test
    void refusesTheBareDomain() throws Exception {
        mvc.perform(post("/login").param("email", "@thapar.edu").param("password", "anything"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Use your @thapar.edu account")));
    }

    @Test
    void showsThePageToSomeoneSignedIn() throws Exception {
        mvc.perform(get("/").session(signedInSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rk@thapar.edu")));
    }

    @Test
    void clearsTheSessionOnTheWayOut() throws Exception {
        MockHttpSession session = signedInSession();

        mvc.perform(get("/logout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertTrue(session.isInvalid());
    }
}
