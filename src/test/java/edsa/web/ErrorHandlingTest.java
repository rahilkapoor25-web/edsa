package edsa.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What a person sees when something goes wrong.
 *
 * <p>MockMvc does not run the servlet container's error dispatch at all: it reports the status
 * and stops, so there is no error page for it to inspect. These tests therefore assert the
 * status, that the template which replaces Spring's default page exists, and the settings that
 * decide what is allowed to appear on it. The rendered page itself was checked by hand against
 * the running application, because that is the only place it exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorHandlingTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Environment environment;

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void anAddressThatDoesNotExistGivesANotFoundRatherThanAServerError() throws Exception {
        mvc.perform(get("/no-such-page")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void nothingAboutTheExceptionIsAllowedIntoTheBrowser() {
        // Whatever goes wrong, the browser is told the status and nothing else. A message or a
        // stack trace would be no use to a coordinator and some use to an attacker.
        assertFalse(Boolean.parseBoolean(environment.getProperty("server.error.include-exception")),
                "server.error.include-exception must stay off");
        for (String setting : new String[] {
                "server.error.include-stacktrace",
                "server.error.include-message",
                "server.error.include-binding-errors"}) {
            org.junit.jupiter.api.Assertions.assertEquals("never", environment.getProperty(setting),
                    setting + " must stay set to never");
        }
    }

    @Test
    void theErrorPageTemplateIsOnTheClasspathSoThereIsNoWhitelabelPage() {
        assertFalse(getClass().getResource("/templates/error.html") == null,
                "templates/error.html is what replaces Spring's default error page");
    }
}
