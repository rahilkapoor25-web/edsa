package edsa.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Getting the administrator account into working order on start-up.
 *
 * <p>This was the one code path that creates a way in, and it had no test at all. It also only
 * acted while the account table was completely empty, which meant that once a single professor
 * had been provisioned there was no way to add an administrator or to recover one whose
 * password had been lost. With a database that now survives restarts, that would have been
 * permanent.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdministratorProvisioningTest {

    private static final String EMAIL = "boss@thapar.edu";
    private static final String PASSWORD = "a-chosen-password";

    @Autowired
    private AccountService accounts;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startFromNoAccounts() {
        jdbc.update("DELETE FROM app_user");
    }

    @Test
    void theAdministratorIsCreatedWhenThereIsNoAccountAtAll() {
        AccountService.AdminOutcome outcome =
                accounts.provisionAdministrator(EMAIL, PASSWORD, "The Office");

        assertEquals(AccountService.AdminOutcome.CREATED, outcome);
        AppUser created = accounts.find(EMAIL).orElseThrow();
        assertEquals("ADMIN", created.role());
        assertTrue(created.active());
        assertTrue(encoder.matches(PASSWORD, created.passwordHash()));
    }

    @Test
    void theAdministratorIsStillCreatedWhenOtherAccountsAlreadyExist() {
        accounts.register("professor@thapar.edu", "professor-password", "A Professor", "PROFESSOR");

        AccountService.AdminOutcome outcome =
                accounts.provisionAdministrator(EMAIL, PASSWORD, "The Office");

        assertEquals(AccountService.AdminOutcome.CREATED, outcome,
                "a non-empty table must not stop an administrator being added");

        assertEquals("ADMIN", accounts.find(EMAIL).orElseThrow().role());
        assertEquals("PROFESSOR", accounts.find("professor@thapar.edu").orElseThrow().role(),
                "and the professor is left exactly as they were");
    }

    @Test
    void aForgottenPasswordCanBeRecoveredByStartingWithANewOne() {
        accounts.provisionAdministrator(EMAIL, "the-old-password", "The Office");
        String oldHash = accounts.find(EMAIL).orElseThrow().passwordHash();

        AccountService.AdminOutcome outcome =
                accounts.provisionAdministrator(EMAIL, "the-new-password", "The Office");

        assertEquals(AccountService.AdminOutcome.PASSWORD_RESET, outcome);
        AppUser after = accounts.find(EMAIL).orElseThrow();
        assertNotEquals(oldHash, after.passwordHash());
        assertTrue(encoder.matches("the-new-password", after.passwordHash()));
        assertFalse(encoder.matches("the-old-password", after.passwordHash()));
    }

    @Test
    void aDisabledAdministratorIsSwitchedBackOn() {
        accounts.provisionAdministrator(EMAIL, PASSWORD, "The Office");
        users.setActive(EMAIL, false);
        assertFalse(accounts.find(EMAIL).orElseThrow().active());

        accounts.provisionAdministrator(EMAIL, PASSWORD, "The Office");

        assertTrue(accounts.find(EMAIL).orElseThrow().active(),
                "locking yourself out by disabling the account should be recoverable");
    }

    @Test
    void anAccountThatWasAProfessorIsPromotedAndItsActivationLinkIsKilled() {
        jdbc.update("INSERT INTO app_user (email, password_hash, display_name, role, active, "
                + "faculty_id, activation_hash, activation_expires_at) "
                + "VALUES (?, 'unusable', 'Dr Example', 'PROFESSOR', FALSE, 'F01', 'a-hash', "
                + "DATEADD('DAY', 7, CURRENT_TIMESTAMP))", EMAIL);

        accounts.provisionAdministrator(EMAIL, PASSWORD, "The Office");

        AppUser after = accounts.find(EMAIL).orElseThrow();
        assertEquals("ADMIN", after.role());
        assertTrue(after.active());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE LOWER(email) = ? AND activation_hash IS NOT NULL",
                Integer.class, EMAIL),
                "a half-finished activation must not be left usable on an administrator account");
    }

    @Test
    void theStoredPasswordIsAlwaysAHashAndNeverThePasswordItself() {
        accounts.provisionAdministrator(EMAIL, PASSWORD, "The Office");

        String hash = accounts.find(EMAIL).orElseThrow().passwordHash();

        assertNotEquals(PASSWORD, hash);
        assertFalse(hash.contains(PASSWORD));
        assertTrue(hash.startsWith("$2"), "a BCrypt hash: " + hash);
    }

    @Test
    void aMalformedAddressOrShortPasswordIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> accounts.provisionAdministrator("not-an-email", PASSWORD, "The Office"));
        assertThrows(IllegalArgumentException.class,
                () -> accounts.provisionAdministrator(EMAIL, "short", "The Office"));

        assertTrue(accounts.all().isEmpty(), "nothing is created from bad settings");
    }

    @Test
    void runningItTwiceWithTheSamePasswordIsHarmless() {
        accounts.provisionAdministrator(EMAIL, PASSWORD, "The Office");
        accounts.provisionAdministrator(EMAIL, PASSWORD, "The Office");

        assertEquals(1, accounts.all().size(), "it must not create a second account");
        assertTrue(encoder.matches(PASSWORD, accounts.find(EMAIL).orElseThrow().passwordHash()));
    }

    @Test
    void theLastAdministratorStillCannotBeDisabledThroughTheOrdinaryRoute() {
        accounts.provisionAdministrator(EMAIL, PASSWORD, "The Office");

        assertThrows(IllegalArgumentException.class, () -> accounts.setActive(EMAIL, false),
                "the existing protection against locking everybody out is untouched");
    }
}
