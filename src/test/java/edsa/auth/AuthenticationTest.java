package edsa.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Signing in is decided by the account table, not by the shape of the address.
 *
 * <p>Accounts here are registered by the test itself; nothing depends on a seeded account, a
 * real person or an external identity provider.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void registerAccounts() {
        jdbc.update("DELETE FROM app_user");
        accounts.register("rahilkapoor1be@thapar.edu", PASSWORD, "Registered Coordinator", "ADMIN");
        accounts.register("registered.person@gmail.com", PASSWORD, "Registered Gmail User", "ADMIN");
        users.create("retired@thapar.edu", encoder.encode(PASSWORD), "Retired Staff", "ADMIN", false);
    }

    @Test
    void aRegisteredAddressWithTheRightPasswordSignsIn() throws Exception {
        mvc.perform(formLogin("/login").user("email", "rahilkapoor1be@thapar.edu").password("password", PASSWORD))
                .andExpect(authenticated().withUsername("rahilkapoor1be@thapar.edu"));
    }

    @Test
    void aRegisteredAddressWithTheWrongPasswordIsRefused() throws Exception {
        mvc.perform(formLogin("/login").user("email", "rahilkapoor1be@thapar.edu").password("password", "not-it"))
                .andExpect(unauthenticated());
    }

    @Test
    void anUnregisteredInstituteAddressIsRefused() throws Exception {
        mvc.perform(formLogin("/login").user("email", "r@thapar.edu").password("password", PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void anUnregisteredGmailAddressIsRefused() throws Exception {
        mvc.perform(formLogin("/login").user("email", "stranger@gmail.com").password("password", PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void aRegisteredGmailAddressWithTheRightPasswordSignsIn() throws Exception {
        mvc.perform(formLogin("/login").user("email", "registered.person@gmail.com").password("password", PASSWORD))
                .andExpect(authenticated().withUsername("registered.person@gmail.com"));
    }

    @Test
    void anInactiveAccountIsRefusedEvenWithTheRightPassword() throws Exception {
        mvc.perform(formLogin("/login").user("email", "retired@thapar.edu").password("password", PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void theAddressIsNotCaseSensitive() throws Exception {
        mvc.perform(formLogin("/login").user("email", "RahilKapoor1BE@Thapar.edu").password("password", PASSWORD))
                .andExpect(authenticated());
    }

    @Test
    void aMalformedAddressCannotBeRegistered() {
        assertFalse(AccountService.isWellFormed("not-an-email"));
        assertFalse(AccountService.isWellFormed("missing@domain"));
        assertFalse(AccountService.isWellFormed("@thapar.edu"));
        assertTrue(AccountService.isWellFormed("someone@thapar.edu"));

        assertThrows(IllegalArgumentException.class,
                () -> accounts.register("not-an-email", PASSWORD, "Nobody", "ADMIN"));
    }

    @Test
    void aMalformedAddressIsRefusedAtLogin() throws Exception {
        mvc.perform(formLogin("/login").user("email", "not-an-email").password("password", PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void anEmptyPasswordIsRefused() throws Exception {
        mvc.perform(formLogin("/login").user("email", "rahilkapoor1be@thapar.edu").password("password", ""))
                .andExpect(unauthenticated());
    }

    @Test
    void passwordsAreStoredHashedNeverInPlaintext() {
        AppUser stored = users.findByEmail("rahilkapoor1be@thapar.edu").orElseThrow();

        assertNotEquals(PASSWORD, stored.passwordHash());
        assertTrue(stored.passwordHash().startsWith("$2"), "expected a BCrypt hash");
        assertTrue(encoder.matches(PASSWORD, stored.passwordHash()));

        String anyColumnHoldingThePassword = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE password_hash = ?", String.class, PASSWORD);
        assertTrue("0".equals(anyColumnHoldingThePassword));
    }

    @Test
    void anAddressCannotBeRegisteredTwice() {
        assertThrows(IllegalArgumentException.class,
                () -> accounts.register("rahilkapoor1be@thapar.edu", PASSWORD, "Duplicate", "ADMIN"));
    }

    @Test
    void aShortPasswordIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> accounts.register("new.person@thapar.edu", "short", "New Person", "ADMIN"));
    }

    @Test
    void protectedPagesSendAStrangerToTheLoginPage() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/rooms")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/workloads")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/import-template.xlsx")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void theLoginPageAndStylesheetStayPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/css/edsa.css")).andExpect(status().isOk());
    }

    @Test
    void loggingOutEndsTheSession() throws Exception {
        mvc.perform(logout("/logout")).andExpect(unauthenticated());
    }
}
