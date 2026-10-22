package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.auth.AccountService;
import edsa.core.LeaveRequest;
import edsa.data.LeaveRequestRepository;
import edsa.data.SampleData;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Whose absence an account is allowed to record.
 *
 * <p>A professor used to be able to file leave for a colleague by editing one hidden form
 * field, which would then have moved that colleague's duties once an administrator confirmed
 * it. The subject of a request now comes from the signed-in account, so these tests send a
 * deliberately forged {@code facultyId} and assert the request was stored against the sender
 * instead.
 *
 * <p>The accounts are linked to faculty ids the way real ones are — the {@code faculty_id}
 * column the workbook import fills — so the identity being tested is the real one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveIdentityTest {

    /** Two invigilators out of the bundled sample data. */
    private static final String OWN = "F01";
    private static final String COLLEAGUE = "F02";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @Autowired
    private LeaveRequestRepository requests;

    @Autowired
    private AccountService accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void loadAPlanAndLinkedAccounts() {
        jdbc.update("DELETE FROM leave_request");
        jdbc.update("DELETE FROM app_user");
        workspace.setData(SampleData.load());

        // A professor's account, tied to faculty record F1 exactly as provisioning does it.
        accounts.register("own@thapar.edu", "professor-password", "Professor Own", "PROFESSOR");
        jdbc.update("UPDATE app_user SET faculty_id = ? WHERE email = 'own@thapar.edu'", OWN);
        // And one with no faculty record behind it, as a hand-made account has.
        accounts.register("unlinked@thapar.edu", "professor-password", "Professor Unlinked", "PROFESSOR");
        accounts.register("boss@thapar.edu", "administrator-password", "The Office", "ADMIN");
    }

    private LeaveRequest onlyRequest() {
        List<LeaveRequest> all = requests.all();
        assertEquals(1, all.size(), "exactly one request should have been stored");
        return all.get(0);
    }

    // ── A professor is held to their own identity ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "own@thapar.edu", roles = "PROFESSOR")
    void aForgedFacultyIdIsIgnoredAndTheAbsenceIsRecordedAgainstTheSender() throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf())
                        .param("facultyId", COLLEAGUE)
                        .param("wholeSession", "yes")
                        .param("reason", "Reported sick"))
                .andExpect(redirectedUrl("/disruption"));

        assertEquals(OWN, onlyRequest().facultyId(),
                "the request belongs to the signed-in professor, not the id they put in the form");
    }

    @Test
    @WithMockUser(username = "own@thapar.edu", roles = "PROFESSOR")
    void aProfessorRecordingWithNoFacultyIdGetsTheirOwnAbsence() throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf())
                        .param("wholeSession", "yes")
                        .param("reason", "Reported sick"))
                .andExpect(redirectedUrl("/disruption"));

        assertEquals(OWN, onlyRequest().facultyId());
    }

    @Test
    @WithMockUser(username = "own@thapar.edu", roles = "PROFESSOR")
    void aColleaguesDutiesAreNeverTouchedByAForgedRequest() throws Exception {
        List<String> colleaguesDuties = workspace.result().plan().duties()
                .forFaculty(COLLEAGUE).stream().map(duty -> duty.slotId()).sorted().toList();

        mvc.perform(post("/disruption/leave").with(csrf())
                .param("facultyId", COLLEAGUE).param("wholeSession", "yes"));

        assertEquals(colleaguesDuties, workspace.result().plan().duties()
                        .forFaculty(COLLEAGUE).stream().map(duty -> duty.slotId()).sorted().toList(),
                "recording changes no duty in any case, and this one is not even theirs");
        assertEquals(OWN, onlyRequest().facultyId());
    }

    @Test
    @WithMockUser(username = "unlinked@thapar.edu", roles = "PROFESSOR")
    void anAccountWithNoFacultyRecordCannotRecordAnythingAtAll() throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf())
                        .param("facultyId", COLLEAGUE)
                        .param("wholeSession", "yes"))
                .andExpect(redirectedUrl("/disruption"));

        assertTrue(requests.all().isEmpty(),
                "with no faculty record of their own there is no absence they can report");
    }

    @Test
    @WithMockUser(username = "unlinked@thapar.edu", roles = "PROFESSOR")
    void theUnlinkedAccountIsToldWhyRatherThanFailingSilently() throws Exception {
        mvc.perform(get("/disruption"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "not linked to a faculty record")));
    }

    // ── The examination office may act for others, by design ─────────────────────────────

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void theOfficeMayRecordAnAbsenceForAnyInvigilator() throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf())
                        .param("facultyId", COLLEAGUE)
                        .param("wholeSession", "yes")
                        .param("reason", "Phoned in"))
                .andExpect(redirectedUrl("/disruption"));

        assertEquals(COLLEAGUE, onlyRequest().facultyId(), "the office records on anybody's behalf");
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void theOfficeMustSayWhoIsAway() throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf()).param("wholeSession", "yes"))
                .andExpect(redirectedUrl("/disruption"));

        assertTrue(requests.all().isEmpty(), "an absence with no subject is refused, not guessed");
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void theOfficeStillSeesTheWholeRoster() throws Exception {
        mvc.perform(get("/disruption"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"facultyId\"")));
    }

    // ── Signed out ───────────────────────────────────────────────────────────────────────

    @Test
    void aStrangerCannotRecordAnAbsenceForAnybody() throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf())
                        .param("facultyId", COLLEAGUE).param("wholeSession", "yes"))
                .andExpect(status().is3xxRedirection());

        assertTrue(requests.all().isEmpty());
    }
}
