package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.auth.AccountService;
import edsa.auth.AppUser;
import edsa.auth.UserRepository;

import org.hamcrest.Matchers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Only an administrator may hand out accounts, and only through this page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAdminTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startWithOneAdministrator() {
        jdbc.update("DELETE FROM app_user");
        jdbc.update("DELETE FROM faculty");
        accounts.register("boss@thapar.edu", "administrator-pass", "The Administrator", "ADMIN");
    }

    private void importedFaculty(String id, String name, String email) {
        jdbc.update("INSERT INTO faculty (id, name, email, department, available) "
                + "VALUES (?, ?, ?, 'CSE', TRUE)", id, name, email);
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void anAdministratorCanCreateAProfessorAccount() throws Exception {
        mvc.perform(post("/users").with(csrf())
                        .param("email", "professor@thapar.edu")
                        .param("displayName", "A Professor")
                        .param("password", "professor-pass")
                        .param("role", "PROFESSOR"))
                .andExpect(redirectedUrl("/users"));

        AppUser created = accounts.find("professor@thapar.edu").orElseThrow();
        assertEquals("PROFESSOR", created.role());
        assertTrue(created.active());
        assertTrue(encoder.matches("professor-pass", created.passwordHash()));
        assertFalse(created.passwordHash().contains("professor-pass"));
    }

    @Test
    @WithMockUser(username = "professor@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotReachTheAccountsPage() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isForbidden());
        mvc.perform(post("/users").with(csrf())
                        .param("email", "sneaky@thapar.edu")
                        .param("displayName", "Sneaky")
                        .param("password", "sneaky-pass-1"))
                .andExpect(status().isForbidden());

        assertTrue(accounts.find("sneaky@thapar.edu").isEmpty());
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void anAccountCanBeDisabledAndEnabledAgain() throws Exception {
        accounts.register("professor@thapar.edu", "professor-pass", "A Professor", "PROFESSOR");

        mvc.perform(post("/users/active").with(csrf())
                .param("email", "professor@thapar.edu").param("active", "false"));
        assertFalse(accounts.find("professor@thapar.edu").orElseThrow().active());

        mvc.perform(post("/users/active").with(csrf())
                .param("email", "professor@thapar.edu").param("active", "true"));
        assertTrue(accounts.find("professor@thapar.edu").orElseThrow().active());
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void theLastAdministratorCannotLockEveryoneOut() throws Exception {
        mvc.perform(post("/users/active").with(csrf())
                .param("email", "boss@thapar.edu").param("active", "false"));

        assertTrue(accounts.find("boss@thapar.edu").orElseThrow().active(),
                "disabling the last administrator would leave nobody who could undo it");
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void aBadAddressOrShortPasswordIsRefusedWithAMessage() throws Exception {
        mvc.perform(post("/users").with(csrf())
                        .param("email", "not-an-email")
                        .param("displayName", "Nobody")
                        .param("password", "long-enough-pass"))
                .andExpect(redirectedUrl("/users"));
        assertTrue(accounts.find("not-an-email").isEmpty());

        mvc.perform(post("/users").with(csrf())
                        .param("email", "fine@thapar.edu")
                        .param("displayName", "Fine")
                        .param("password", "short"))
                .andExpect(redirectedUrl("/users"));
        assertTrue(accounts.find("fine@thapar.edu").isEmpty());
    }

    // ── Bulk creation from the imported faculty records ───────────────────────────────────

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void thePageListsTheImportedProfessorsAwaitingAnAccount() throws Exception {
        importedFaculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu");

        mvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Professors from the imported workbook")))
                .andExpect(content().string(Matchers.containsString("Dr Ayaan Example")))
                .andExpect(content().string(Matchers.containsString("Ready to invite")));

        assertTrue(accounts.find("ayaan@thapar.edu").isEmpty(), "looking creates nothing");
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void anAdministratorCanCreateEveryMissingProfessorAccountAtOnce() throws Exception {
        importedFaculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu");
        importedFaculty("F02", "Dr Bela Example", "bela@thapar.edu");

        mvc.perform(post("/users/professors").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Accounts just created")))
                .andExpect(content().string(Matchers.containsString("/activate?token=")));

        AppUser created = accounts.find("ayaan@thapar.edu").orElseThrow();
        assertEquals("PROFESSOR", created.role());
        assertEquals("F01", created.facultyId());
        assertFalse(created.active(), "it cannot be signed into until its owner sets a password");
        assertTrue(accounts.find("bela@thapar.edu").isPresent());
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void theActivationLinkIsShownOnceAndNotAgainOnTheNextPageLoad() throws Exception {
        importedFaculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu");
        mvc.perform(post("/users/professors").with(csrf()))
                .andExpect(content().string(Matchers.containsString("/activate?token=")));

        mvc.perform(get("/users"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/activate?token="))));
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void runningBulkCreationTwiceCreatesNothingTheSecondTime() throws Exception {
        importedFaculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu");
        mvc.perform(post("/users/professors").with(csrf()));
        String hashBefore = accounts.find("ayaan@thapar.edu").orElseThrow().passwordHash();

        mvc.perform(post("/users/professors").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("already has an account")));

        assertEquals(hashBefore, accounts.find("ayaan@thapar.edu").orElseThrow().passwordHash());
        assertEquals(2, users.count(), "the administrator plus the one professor");
    }

    @Test
    @WithMockUser(username = "professor@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotCreateAccountsInBulk() throws Exception {
        importedFaculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu");

        mvc.perform(post("/users/professors").with(csrf())).andExpect(status().isForbidden());

        assertTrue(accounts.find("ayaan@thapar.edu").isEmpty());
        assertEquals(1, users.count(), "only the administrator the test started with");
    }

    @Test
    void aStrangerCannotCreateAccountsInBulk() throws Exception {
        importedFaculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu");

        mvc.perform(post("/users/professors").with(csrf())).andExpect(status().is3xxRedirection());

        assertTrue(accounts.find("ayaan@thapar.edu").isEmpty());
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void bulkCreationNeedsACsrfToken() throws Exception {
        importedFaculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu");

        mvc.perform(post("/users/professors")).andExpect(status().isForbidden());

        assertTrue(accounts.find("ayaan@thapar.edu").isEmpty());
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void aRecordThatCannotBeUsedIsExplainedOnThePage() throws Exception {
        importedFaculty("F01", "Dr Ayaan Example", null);

        mvc.perform(post("/users/professors").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("No email address")));

        assertEquals(1, users.count(), "no account was invented for them");
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void thePageSaysSoWhenNoWorkbookHasBeenImported() throws Exception {
        mvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("No faculty records have been imported")));
    }
}
