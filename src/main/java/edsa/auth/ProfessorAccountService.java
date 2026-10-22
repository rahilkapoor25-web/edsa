package edsa.auth;

import edsa.data.ExamDataRepository;
import edsa.data.FacultyContact;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates professors' accounts from the faculty records the administrator already imported.
 *
 * <p>Nothing is invented here: every account comes from a row of the Faculty worksheet, is
 * tied to that row's faculty id, and is given the {@code PROFESSOR} role. There is no second
 * upload — the records are read back out of the database the workbook import filled.
 *
 * <p><strong>No password is ever generated.</strong> A password derived from a name, an id or
 * an email is guessable by anyone holding the spreadsheet, so instead each account is created
 * disabled, holding the hash of a 256-bit random single-use secret that expires. The professor
 * sets their own password through that link, and only then can the account be signed into.
 * Email delivery is not configured in this application, so the links are handed to the
 * administrator once, on screen, to pass on — they are never stored in readable form, never
 * logged, and never shown twice.
 */
@Service
public class ProfessorAccountService {

    /** 256 bits of randomness: long enough that guessing is not a consideration. */
    private static final int TOKEN_BYTES = 32;

    /**
     * Domains reserved for documentation, which can never receive mail.
     *
     * <p>They arrive by the obvious route: the downloadable template fills its example rows
     * with addresses like {@code f01@example.edu}, somebody replaces the names but not the
     * addresses, and the import carries them through. Creating accounts for them would
     * produce invitations nobody can ever be given, so a record like this is refused with a
     * message saying to put the real institutional address in the workbook.
     *
     * <p>Reserved by RFC 2606 and IANA precisely so they cannot belong to anybody.
     */
    private static final Set<String> UNREACHABLE_DOMAINS =
            Set.of("example.com", "example.org", "example.net", "example.edu", "example");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final ExamDataRepository facultyRecords;
    private final PasswordEncoder encoder;
    private final AccountProperties properties;

    public ProfessorAccountService(UserRepository users, ExamDataRepository facultyRecords,
                                   PasswordEncoder encoder, AccountProperties properties) {
        this.users = users;
        this.facultyRecords = facultyRecords;
        this.encoder = encoder;
        this.properties = properties;
    }

    /**
     * What a bulk creation would do, without doing any of it.
     *
     * <p>This is the confirmation step: the administrator sees how many accounts would be
     * created and which records cannot be used before anything is inserted.
     */
    public List<ProvisioningCandidate> preview() {
        Set<String> facultyWithAccounts = users.facultyIdsWithAccounts();
        Set<String> emailsSeen = new HashSet<>();
        List<ProvisioningCandidate> candidates = new ArrayList<>();

        for (FacultyContact contact : facultyRecords.facultyContacts()) {
            candidates.add(assess(contact, facultyWithAccounts, emailsSeen));
        }
        return List.copyOf(candidates);
    }

    /** Whether there is anything for the administrator to confirm. */
    public long eligibleCount() {
        return preview().stream().filter(ProvisioningCandidate::isEligible).count();
    }

    private ProvisioningCandidate assess(FacultyContact contact, Set<String> facultyWithAccounts,
                                         Set<String> emailsSeen) {
        String id = contact.id();
        String name = contact.name();
        String department = contact.department();
        String email = contact.email() == null ? null : contact.email().trim().toLowerCase();

        if (id == null || id.isBlank()) {
            return ProvisioningCandidate.unusable(id, name, email, department,
                    "The record has no faculty id");
        }
        if (facultyWithAccounts.contains(id)) {
            return ProvisioningCandidate.skipped(id, name, email, department, "Already has an account");
        }
        if (name == null || name.isBlank()) {
            return ProvisioningCandidate.unusable(id, name, email, department, "The record has no name");
        }
        if (email == null || email.isBlank()) {
            return ProvisioningCandidate.unusable(id, name, email, department,
                    "No email address on the Faculty worksheet");
        }
        if (!AccountService.isWellFormed(email)) {
            return ProvisioningCandidate.unusable(id, name, email, department,
                    "'" + email + "' is not an email address");
        }
        if (isUnreachable(email)) {
            return ProvisioningCandidate.unusable(id, name, email, department,
                    "'" + email + "' is an example address, not a real mailbox. Put this "
                            + "professor's institutional address in the Faculty worksheet and "
                            + "import the workbook again.");
        }
        if (!emailsSeen.add(email)) {
            return ProvisioningCandidate.unusable(id, name, email, department,
                    "Another faculty record uses the same address");
        }
        Optional<AppUser> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            return ProvisioningCandidate.skipped(id, name, email, department,
                    existing.get().isLinkedToFaculty()
                            ? "That address already belongs to another faculty record"
                            : "An account already exists for that address");
        }
        return ProvisioningCandidate.eligible(id, name, email, department);
    }

    /**
     * Creates an account for every eligible faculty record.
     *
     * <p>One transaction for the whole batch: if any insert fails the lot is rolled back, so
     * there is no half-provisioned state to reason about. An account that already exists is
     * skipped untouched — its password, role and faculty mapping are never rewritten, so
     * running this twice is safe and the second run creates nothing.
     */
    @Transactional
    public ProvisioningSummary createMissingAccounts() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(properties.getActivationValidHours());
        List<ProvisionedAccount> created = new ArrayList<>();
        List<ProvisioningCandidate> skipped = new ArrayList<>();
        List<ProvisioningCandidate> unusable = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (ProvisioningCandidate candidate : preview()) {
            switch (candidate.eligibility()) {
                case SKIPPED -> skipped.add(candidate);
                case UNUSABLE -> unusable.add(candidate);
                case ELIGIBLE -> {
                    try {
                        created.add(create(candidate, expiresAt));
                    } catch (DuplicateKeyException race) {
                        // Somebody else created it between the preview and now.
                        failures.add(candidate.name() + " (" + candidate.facultyId()
                                + "): an account for that address or faculty record already exists");
                    }
                }
                default -> throw new IllegalStateException("unhandled eligibility " + candidate.eligibility());
            }
        }
        return ProvisioningSummary.of(created, skipped, unusable, failures);
    }

    private ProvisionedAccount create(ProvisioningCandidate candidate, LocalDateTime expiresAt) {
        String secret = randomSecret();
        // The stored password hash is of a secret nobody keeps, so no password can match it
        // until the professor sets one. The account is disabled until then in any case.
        users.createAwaitingActivation(candidate.email(), encoder.encode(randomSecret()),
                candidate.name(), candidate.facultyId(), encoder.encode(secret), expiresAt);

        AppUser stored = users.findByEmail(candidate.email())
                .orElseThrow(() -> new IllegalStateException("the account was not stored"));
        return new ProvisionedAccount(candidate.facultyId(), candidate.name(), stored.email(),
                stored.id() + "." + secret);
    }

    /**
     * Sets the password an activation link's owner chose, and enables their account.
     *
     * <p>The secret is verified before anything else is said about the link, so only somebody
     * actually holding it can be told that it has expired.
     *
     * @return the address that was activated
     * @throws IllegalArgumentException when the link is not valid, has expired or has been
     *                                 used, or the password is too short
     */
    @Transactional
    public String activate(String token, String rawPassword) {
        PendingActivation pending = verify(token);
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("The password must be at least 8 characters");
        }
        if (!users.activate(pending.accountId(), encoder.encode(rawPassword))) {
            throw new IllegalArgumentException("That activation link has already been used");
        }
        return pending.email();
    }

    /** Whether a link is worth showing a password form for, without consuming it. */
    public boolean isActivatable(String token) {
        try {
            verify(token);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private PendingActivation verify(String token) {
        IllegalArgumentException refused =
                new IllegalArgumentException("That activation link is not valid");
        if (token == null || token.isBlank()) {
            throw refused;
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw refused;
        }
        long accountId;
        try {
            accountId = Long.parseLong(token.substring(0, separator));
        } catch (NumberFormatException notANumber) {
            throw refused;
        }
        String secret = token.substring(separator + 1);

        PendingActivation pending = users.pendingActivation(accountId).orElseThrow(() -> refused);
        if (pending.activationHash() == null || !encoder.matches(secret, pending.activationHash())) {
            throw refused;
        }
        if (pending.hasExpired(LocalDateTime.now())) {
            throw new IllegalArgumentException("That activation link expired on "
                    + pending.expiresAt().toLocalDate() + ". Ask the examination office for a new one.");
        }
        return pending;
    }

    /** Whether this address belongs to a domain reserved for documentation. */
    private static boolean isUnreachable(String email) {
        int at = email.lastIndexOf('@');
        return at >= 0 && UNREACHABLE_DOMAINS.contains(email.substring(at + 1));
    }

    private static String randomSecret() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
