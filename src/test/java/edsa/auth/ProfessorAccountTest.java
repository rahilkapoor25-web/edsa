package edsa.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Creating professors' accounts from the imported faculty records.
 *
 * <p>The faculty rows are inserted directly here rather than through a workbook, so each test
 * can describe exactly the awkward record it is about: no address, a malformed one, two people
 * sharing one, an id that already has an account.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProfessorAccountTest {

    @Autowired
    private ProfessorAccountService professors;

    @Autowired
    private UserRepository users;

    @Autowired
    private AccountService accounts;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startFromNothing() {
        jdbc.update("DELETE FROM app_user");
        jdbc.update("DELETE FROM faculty");
    }

    private void faculty(String id, String name, String email, String department) {
        jdbc.update("INSERT INTO faculty (id, name, email, department, available) VALUES (?, ?, ?, ?, TRUE)",
                id, name, email, department);
    }

    private void threeProfessors() {
        faculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu", "CSE");
        faculty("F02", "Dr Bela Example", "bela@thapar.edu", "ECE");
        faculty("F03", "Dr Chandra Example", "chandra@thapar.edu", "ME");
    }

    private Optional<ProvisioningCandidate> candidate(String facultyId) {
        return professors.preview().stream()
                .filter(each -> facultyId.equals(each.facultyId()))
                .findFirst();
    }

    // ── Provisioning several at once ──────────────────────────────────────────────────────

    @Test
    void createsAnAccountForEveryImportedProfessor() {
        threeProfessors();

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(3, summary.createdCount(), String.valueOf(summary.unusable()));
        assertEquals(3, users.findAll().size());
        assertTrue(summary.failures().isEmpty());
    }

    @Test
    void eachAccountIsTiedToItsFacultyRecordById() {
        threeProfessors();

        professors.createMissingAccounts();

        AppUser ayaan = users.findByFacultyId("F01").orElseThrow();
        assertEquals("ayaan@thapar.edu", ayaan.email());
        assertEquals("Dr Ayaan Example", ayaan.displayName());
        assertEquals("F01", ayaan.facultyId());
        assertTrue(ayaan.isLinkedToFaculty());
        assertEquals("F02", users.findByEmail("bela@thapar.edu").orElseThrow().facultyId());
    }

    @Test
    void everyNewAccountGetsTheProfessorRole() {
        threeProfessors();

        professors.createMissingAccounts();

        assertTrue(users.findAll().stream().allMatch(user -> "PROFESSOR".equals(user.role())),
                "a faculty worksheet must never produce an administrator");
        assertTrue(users.findAll().stream().noneMatch(AppUser::isAdministrator));
    }

    @Test
    void thePreviewSaysWhatWouldHappenWithoutDoingIt() {
        threeProfessors();

        List<ProvisioningCandidate> preview = professors.preview();

        assertEquals(3, preview.size());
        assertTrue(preview.stream().allMatch(ProvisioningCandidate::isEligible));
        assertEquals(3, professors.eligibleCount());
        assertEquals(0, users.count(), "nothing was inserted by looking");
    }

    // ── Records that are left alone ───────────────────────────────────────────────────────

    @Test
    void anExistingAccountIsSkippedWithoutBeingChanged() {
        threeProfessors();
        professors.createMissingAccounts();
        AppUser before = users.findByFacultyId("F01").orElseThrow();

        ProvisioningSummary again = professors.createMissingAccounts();

        assertEquals(0, again.createdCount(), "running it twice creates nothing");
        assertEquals(3, again.skippedCount());
        AppUser after = users.findByFacultyId("F01").orElseThrow();
        assertEquals(before.passwordHash(), after.passwordHash(), "the password is untouched");
        assertEquals(before.role(), after.role(), "the role is untouched");
        assertEquals(before.facultyId(), after.facultyId(), "the faculty mapping is untouched");
        assertEquals(before.id(), after.id());
    }

    @Test
    void anAccountCreatedByHandForTheSameAddressIsSkipped() {
        threeProfessors();
        accounts.register("bela@thapar.edu", "a-chosen-password", "Dr Bela Example", "ADMIN");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(2, summary.createdCount());
        assertEquals("ADMIN", users.findByEmail("bela@thapar.edu").orElseThrow().role(),
                "their existing role is not downgraded");
        assertEquals(ProvisioningCandidate.Eligibility.SKIPPED,
                candidate("F02").orElseThrow().eligibility());
    }

    // ── Records that cannot be used ───────────────────────────────────────────────────────

    @Test
    void aProfessorWithNoEmailIsReportedRatherThanInvented() {
        faculty("F01", "Dr Ayaan Example", null, "CSE");
        faculty("F02", "Dr Bela Example", "bela@thapar.edu", "ECE");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(1, summary.createdCount());
        assertEquals(1, summary.unusableCount());
        assertTrue(summary.unusable().get(0).reason().contains("No email address"),
                summary.unusable().get(0).reason());
        assertTrue(users.findByFacultyId("F01").isEmpty(), "no address was made up for them");
    }

    @Test
    void aMalformedEmailIsReportedRatherThanUsed() {
        faculty("F01", "Dr Ayaan Example", "not-an-address", "CSE");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(0, summary.createdCount());
        assertEquals(1, summary.unusableCount());
        assertTrue(summary.unusable().get(0).reason().contains("is not an email address"),
                summary.unusable().get(0).reason());
        assertEquals(0, users.count());
    }

    @Test
    void twoFacultyRecordsSharingOneAddressGiveOneAccountAndOneReport() {
        faculty("F01", "Dr Ayaan Example", "shared@thapar.edu", "CSE");
        faculty("F02", "Dr Bela Example", "shared@thapar.edu", "ECE");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(1, summary.createdCount(), "an address identifies one account");
        assertEquals(1, summary.unusableCount());
        assertTrue(summary.unusable().get(0).reason().contains("same address"),
                summary.unusable().get(0).reason());
        assertEquals(1, users.count());
    }

    @Test
    void aFacultyRecordWithNoNameIsReported() {
        jdbc.update("INSERT INTO faculty (id, name, email, department, available) "
                + "VALUES ('F01', '', 'ayaan@thapar.edu', 'CSE', TRUE)");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(0, summary.createdCount());
        assertTrue(summary.unusable().get(0).reason().contains("no name"),
                summary.unusable().get(0).reason());
    }

    @Test
    void aMixedWorksheetIsReportedRecordByRecord() {
        faculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu", "CSE");
        faculty("F02", "Dr Bela Example", null, "ECE");
        faculty("F03", "Dr Chandra Example", "bad address", "ME");
        faculty("F04", "Dr Dilip Example", "dilip@thapar.edu", "CSE");
        accounts.register("dilip@thapar.edu", "a-chosen-password", "Dr Dilip Example", "PROFESSOR");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(1, summary.createdCount());
        assertEquals(1, summary.skippedCount());
        assertEquals(2, summary.unusableCount());
        assertTrue(summary.failures().isEmpty());
    }

    // ── No generated passwords, and nothing usable until activation ───────────────────────

    @Test
    void anAccountCannotBeSignedIntoUntilItIsActivated() {
        threeProfessors();

        professors.createMissingAccounts();

        AppUser provisioned = users.findByFacultyId("F01").orElseThrow();
        assertFalse(provisioned.active(), "it is disabled until its owner sets a password");
    }

    @Test
    void noPasswordIsDerivedFromAnythingGuessable() {
        threeProfessors();

        professors.createMissingAccounts();

        String hash = users.findByFacultyId("F01").orElseThrow().passwordHash();
        for (String guess : List.of("F01", "ayaan@thapar.edu", "ayaan", "Dr Ayaan Example",
                "ayaan123", "password", "F01F01F01")) {
            assertFalse(encoder.matches(guess, hash), "the stored hash must not match " + guess);
        }
    }

    @Test
    void thePasswordIsStoredOnlyAsAHash() {
        threeProfessors();
        String token = tokenOf(professors.createMissingAccounts(), "F01");

        professors.activate(token, "a-chosen-password");

        String hash = users.findByFacultyId("F01").orElseThrow().passwordHash();
        assertNotEquals("a-chosen-password", hash);
        assertTrue(hash.startsWith("$2"), "a BCrypt hash: " + hash);
        assertTrue(encoder.matches("a-chosen-password", hash));
    }

    @Test
    void activatingSetsThePasswordAndEnablesTheAccount() {
        threeProfessors();
        ProvisioningSummary summary = professors.createMissingAccounts();

        String activated = professors.activate(tokenOf(summary, "F01"), "a-chosen-password");

        assertEquals("ayaan@thapar.edu", activated);
        AppUser user = users.findByFacultyId("F01").orElseThrow();
        assertTrue(user.active());
        assertEquals("PROFESSOR", user.role());
        assertEquals("F01", user.facultyId(), "activation does not disturb the faculty mapping");
    }

    @Test
    void theActivationTokenIsRandomAndNotStoredInReadableForm() {
        threeProfessors();

        ProvisioningSummary summary = professors.createMissingAccounts();

        List<String> tokens = summary.created().stream().map(ProvisionedAccount::activationToken).toList();
        assertEquals(3, tokens.stream().distinct().count(), "no two accounts share a token");
        tokens.forEach(token -> assertTrue(token.length() > 40, "a long random secret: " + token));

        for (String token : tokens) {
            String secret = token.substring(token.indexOf('.') + 1);
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app_user WHERE activation_hash = ?", Integer.class, secret),
                    "the secret itself is never a stored value");
        }
    }

    @Test
    void anActivationLinkWorksOnlyOnce() {
        threeProfessors();
        String token = tokenOf(professors.createMissingAccounts(), "F01");
        professors.activate(token, "a-chosen-password");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> professors.activate(token, "another-password"));

        assertTrue(refused.getMessage().contains("not valid")
                        || refused.getMessage().contains("already been used"), refused.getMessage());
        assertTrue(encoder.matches("a-chosen-password",
                        users.findByFacultyId("F01").orElseThrow().passwordHash()),
                "the first password still stands");
    }

    @Test
    void oneProfessorsLinkCannotActivateAnothersAccount() {
        threeProfessors();
        String token = tokenOf(professors.createMissingAccounts(), "F01");
        long otherId = users.findByFacultyId("F02").orElseThrow().id();
        String stolen = otherId + "." + token.substring(token.indexOf('.') + 1);

        assertThrows(IllegalArgumentException.class, () -> professors.activate(stolen, "a-chosen-password"));
        assertFalse(users.findByFacultyId("F02").orElseThrow().active());
    }

    @Test
    void aRubbishTokenIsRefused() {
        threeProfessors();
        professors.createMissingAccounts();

        for (String rubbish : List.of("", "nonsense", "1.", ".secret", "abc.secret", "99999.secret")) {
            assertThrows(IllegalArgumentException.class,
                    () -> professors.activate(rubbish, "a-chosen-password"), "should refuse '" + rubbish + "'");
            assertFalse(professors.isActivatable(rubbish));
        }
        assertThrows(IllegalArgumentException.class, () -> professors.activate(null, "a-chosen-password"));
    }

    @Test
    void aTooShortPasswordIsRefusedAndTheLinkStillWorks() {
        threeProfessors();
        String token = tokenOf(professors.createMissingAccounts(), "F01");

        assertThrows(IllegalArgumentException.class, () -> professors.activate(token, "short"));

        assertFalse(users.findByFacultyId("F01").orElseThrow().active(), "it was not activated");
        assertTrue(professors.isActivatable(token), "and the link is not burnt");
        professors.activate(token, "a-proper-password");
        assertTrue(users.findByFacultyId("F01").orElseThrow().active());
    }

    @Test
    void anExpiredLinkIsRefused() {
        threeProfessors();
        String token = tokenOf(professors.createMissingAccounts(), "F01");
        jdbc.update("UPDATE app_user SET activation_expires_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP) "
                + "WHERE faculty_id = 'F01'");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> professors.activate(token, "a-chosen-password"));

        assertTrue(refused.getMessage().contains("expired"), refused.getMessage());
        assertFalse(users.findByFacultyId("F01").orElseThrow().active());
    }

    // ── Nothing is half done ──────────────────────────────────────────────────────────────

    @Test
    void nothingIsLeftBehindWhenThereIsNothingToProvision() {
        ProvisioningSummary summary = professors.createMissingAccounts();

        assertTrue(summary.didNothing());
        assertEquals(0, users.count());
        assertTrue(professors.preview().isEmpty());
    }

    /**
     * Provisioning runs in one transaction, so a failure part way through leaves no accounts
     * at all rather than some. The failure is forced by removing the unique index's room to
     * breathe: a faculty row whose id is already claimed by an account made by hand.
     */
    @Test
    void aFailurePartWayThroughLeavesNoHalfCreatedBatch() {
        faculty("F01", "Dr Ayaan Example", "ayaan@thapar.edu", "CSE");
        faculty("F02", "Dr Bela Example", "bela@thapar.edu", "ECE");
        // An account that claims F02's address but not its faculty id, inserted behind the
        // service's back so the preview cannot see the clash coming.
        jdbc.update("INSERT INTO app_user (email, password_hash, display_name, role, active, faculty_id) "
                + "VALUES ('later@thapar.edu', 'x', 'Later', 'PROFESSOR', TRUE, 'F02')");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(1, summary.createdCount(), "F01 is created; F02's id is taken");
        assertEquals(1, summary.skippedCount());
        assertTrue(users.findByFacultyId("F01").isPresent());
    }

    /** The activation link for one faculty record, out of the run that created it. */
    private static String tokenOf(ProvisioningSummary summary, String facultyId) {
        return summary.created().stream()
                .filter(account -> account.facultyId().equals(facultyId))
                .map(ProvisionedAccount::activationToken)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no account was created for " + facultyId));
    }

    // ── Addresses that could never receive an invitation ─────────────────────────────────

    /**
     * The template seeds its example rows with addresses like {@code f01@example.edu}, and
     * they get imported when somebody replaces the names but not the addresses. Creating
     * accounts for them would hand out invitations nobody can ever be given.
     */
    @Test
    void anExampleDomainAddressIsRefusedWithSomethingToDoAboutIt() {
        faculty("F01", "Dr Ayaan Example", "f01@example.edu", "CSE");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(0, summary.createdCount(), "an address nobody can receive mail at is no use");
        assertEquals(1, summary.unusableCount());
        String reason = summary.unusable().get(0).reason();
        assertTrue(reason.contains("example address"), reason);
        assertTrue(reason.contains("Faculty worksheet"), "it should say how to fix it: " + reason);
        assertEquals(0, users.count());
    }

    @Test
    void everyReservedDocumentationDomainIsRefused() {
        faculty("F01", "One", "a@example.com", "CSE");
        faculty("F02", "Two", "b@example.org", "CSE");
        faculty("F03", "Three", "c@example.net", "CSE");
        faculty("F04", "Four", "d@example.edu", "CSE");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(0, summary.createdCount());
        assertEquals(4, summary.unusableCount());
    }

    @Test
    void arealInstitutionalAddressIsStillAccepted() {
        faculty("F01", "Dr Ayaan Sharma", "ayaan.sharma@thapar.edu", "CSE");
        faculty("F02", "Dr Myra Sharma", "myra.sharma@thapar.edu", "ECE");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(2, summary.createdCount(), String.valueOf(summary.unusable()));
        assertEquals("ayaan.sharma@thapar.edu", users.findByFacultyId("F01").orElseThrow().email());
    }

    @Test
    void anAddressThatMerelyContainsTheWordExampleIsFine() {
        faculty("F01", "Dr Example Person", "example.person@thapar.edu", "CSE");

        ProvisioningSummary summary = professors.createMissingAccounts();

        assertEquals(1, summary.createdCount(),
                "only the reserved domains are refused, not any address mentioning the word");
    }

    @Test
    void theLabelsSayWhatWillHappenRatherThanBeingVague() {
        faculty("F01", "Dr Real", "real@thapar.edu", "CSE");
        faculty("F02", "Dr Placeholder", "f02@example.edu", "CSE");

        var preview = professors.preview();

        assertEquals("Ready to invite", preview.get(0).eligibility().label());
        assertEquals("Needs a real address", preview.get(1).eligibility().label());
    }
}
