package edsa.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.core.Assignment;
import edsa.core.LeaveRequest;
import edsa.core.LeaveStatus;
import edsa.data.LeaveRequestRepository;

import java.time.LocalDate;
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
 * Sick leave through the page: recorded, reviewed, and only then acted on.
 *
 * <p>The point of every test here is that the duty roster moves at exactly one moment — when
 * an administrator confirms — and at no other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisruptionPageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @Autowired
    private LeaveRequestRepository requests;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void loadAPlanAndForgetEveryRequest() {
        jdbc.update("DELETE FROM leave_request");
        workspace.setData(edsa.data.SampleData.load());
    }

    private String someoneOnDuty() {
        return workspace.result().plan().duties().all().get(0).facultyId();
    }

    private LocalDate firstExamDay() {
        return workspace.result().plan().schedule().get(0).getDate();
    }

    private List<String> dutiesOf(String facultyId) {
        return workspace.result().plan().duties().forFaculty(facultyId).stream()
                .map(Assignment::slotId).sorted().toList();
    }

    /** Records an absence for the whole session and returns the stored request. */
    private LeaveRequest recordWholeSession(String facultyId) throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf())
                        .param("facultyId", facultyId)
                        .param("wholeSession", "yes")
                        .param("reason", "Reported sick"))
                .andExpect(redirectedUrl("/disruption"));
        return requests.all().get(0);
    }

    // ── The page ──────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void thePageListsEveryInvigilatorWithTheirDutyCount() throws Exception {
        mvc.perform(get("/disruption"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Disruption")))
                .andExpect(content().string(containsString("duties")))
                .andExpect(content().string(containsString("Report an absence")));
    }

    // ── Recording changes nothing ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void recordingAnAbsenceLeavesTheRosterUntouched() throws Exception {
        String away = someoneOnDuty();
        List<String> before = dutiesOf(away);

        LeaveRequest recorded = recordWholeSession(away);

        assertEquals(LeaveStatus.PENDING, recorded.status());
        assertEquals(before, dutiesOf(away), "a pending request moves no duty");
        assertNull(workspace.lastRepair(), "and nothing was repaired");
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void aPendingRequestIsShownAsAwaitingConfirmation() throws Exception {
        recordWholeSession(someoneOnDuty());

        mvc.perform(get("/disruption"))
                .andExpect(content().string(containsString("Leave requests")))
                .andExpect(content().string(containsString("Pending")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void aPendingRequestSurvivesBeingReadBackFromTheDatabase() throws Exception {
        LeaveRequest recorded = recordWholeSession(someoneOnDuty());

        LeaveRequest reloaded = requests.find(recorded.id()).orElseThrow();

        assertEquals(LeaveStatus.PENDING, reloaded.status());
        assertEquals(recorded.facultyId(), reloaded.facultyId());
        assertEquals("Reported sick", reloaded.reason());
        assertTrue(reloaded.window().isWholeSession());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void anAbsenceForAnUnknownInvigilatorIsRefused() throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf())
                        .param("facultyId", "NOBODY").param("wholeSession", "yes"))
                .andExpect(redirectedUrl("/disruption"));

        assertTrue(requests.all().isEmpty(), "nothing was recorded");
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void anAbsenceWithNoPeriodIsRefused() throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf()).param("facultyId", someoneOnDuty()))
                .andExpect(redirectedUrl("/disruption"));

        assertTrue(requests.all().isEmpty());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void anUnparseableAbsencePeriodIsRefused() throws Exception {
        mvc.perform(post("/disruption/leave").with(csrf())
                        .param("facultyId", someoneOnDuty())
                        .param("from", "16-11-2026").param("to", "16-11-2026"))
                .andExpect(redirectedUrl("/disruption"));

        assertTrue(requests.all().isEmpty());
    }

    // ── Rejecting changes nothing ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void rejectingARequestLeavesTheRosterUntouched() throws Exception {
        String away = someoneOnDuty();
        List<String> before = dutiesOf(away);
        LeaveRequest recorded = recordWholeSession(away);

        mvc.perform(post("/disruption/leave/" + recorded.id() + "/reject").with(csrf()))
                .andExpect(redirectedUrl("/disruption"));

        assertEquals(LeaveStatus.REJECTED, requests.find(recorded.id()).orElseThrow().status());
        assertEquals(before, dutiesOf(away), "a rejected request moves no duty");
        assertNull(workspace.lastRepair());
    }

    // ── Confirming is the only thing that moves a duty ────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void confirmingAnAbsenceReassignsTheirRoomsAndShowsTheDiff() throws Exception {
        String away = someoneOnDuty();
        int dutiesBefore = workspace.result().plan().duties().all().size();
        LeaveRequest recorded = recordWholeSession(away);

        mvc.perform(post("/disruption/leave/" + recorded.id() + "/confirm").with(csrf()))
                .andExpect(redirectedUrl("/disruption"));

        assertEquals(LeaveStatus.COMPLETED, requests.find(recorded.id()).orElseThrow().status());
        assertTrue(dutiesOf(away).isEmpty(), "they keep no duties");
        assertEquals(dutiesBefore, workspace.result().plan().duties().all().size(),
                "every room still has an invigilator");
        assertEquals(away, workspace.lastRepair().absent().getId());

        mvc.perform(get("/disruption"))
                .andExpect(content().string(containsString("What changed")))
                .andExpect(content().string(containsString("was confirmed absent")));
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void onlyTheDutiesInsideTheAbsenceAreReassigned() throws Exception {
        String away = someoneOnDuty();
        LocalDate firstDay = firstExamDay();
        List<String> onOtherDays = workspace.result().plan().duties().forFaculty(away).stream()
                .filter(duty -> !workspace.result().plan().slot(duty.slotId()).getDate().equals(firstDay))
                .map(Assignment::slotId).sorted().toList();

        mvc.perform(post("/disruption/leave").with(csrf())
                .param("facultyId", away)
                .param("from", firstDay + "T00:00")
                .param("to", firstDay.plusDays(1) + "T00:00"));
        LeaveRequest recorded = requests.all().get(0);

        mvc.perform(post("/disruption/leave/" + recorded.id() + "/confirm").with(csrf()));

        assertEquals(onOtherDays, dutiesOf(away), "duties outside the absence stay with them");
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void theReplacementIsConflictFreeAndTheWholePlanIsRecheckeed() throws Exception {
        LeaveRequest recorded = recordWholeSession(someoneOnDuty());

        mvc.perform(post("/disruption/leave/" + recorded.id() + "/confirm").with(csrf()));

        assertTrue(workspace.result().checks().stream().allMatch(check -> check.satisfied()),
                "the repaired plan is re-checked and still holds: " + workspace.result().problems());
        mvc.perform(get("/workloads")).andExpect(status().isOk());
        mvc.perform(get("/exam-plan.xlsx")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void confirmingAnAbsenceForSomebodyWithNoDutiesChangesNothing() throws Exception {
        String idle = workspace.result().plan().workloads().stream()
                .filter(workload -> workload.duties() == 0)
                .findFirst().orElseThrow().member().getId();
        LeaveRequest recorded = recordWholeSession(idle);

        mvc.perform(post("/disruption/leave/" + recorded.id() + "/confirm").with(csrf()));

        assertEquals(LeaveStatus.CONFIRMED, requests.find(recorded.id()).orElseThrow().status(),
                "confirmed, but there was nothing to reassign so it is not completed");
        assertNull(workspace.lastRepair(), "nothing was applied");
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void aRequestCannotBeDecidedTwice() throws Exception {
        LeaveRequest recorded = recordWholeSession(someoneOnDuty());
        mvc.perform(post("/disruption/leave/" + recorded.id() + "/reject").with(csrf()));

        mvc.perform(post("/disruption/leave/" + recorded.id() + "/confirm").with(csrf()))
                .andExpect(redirectedUrl("/disruption"));

        assertEquals(LeaveStatus.REJECTED, requests.find(recorded.id()).orElseThrow().status(),
                "the first decision stands");
        assertNull(workspace.lastRepair());
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void decidingARequestThatDoesNotExistIsRefused() throws Exception {
        mvc.perform(post("/disruption/leave/999999/confirm").with(csrf()))
                .andExpect(redirectedUrl("/disruption"));

        assertNull(workspace.lastRepair());
    }

    // ── Who may do what ──────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void aProfessorMayRecordAnAbsenceButNotConfirmOne() throws Exception {
        String away = someoneOnDuty();
        List<String> before = dutiesOf(away);
        LeaveRequest recorded = recordWholeSession(away);

        mvc.perform(post("/disruption/leave/" + recorded.id() + "/confirm").with(csrf()))
                .andExpect(status().isForbidden());

        assertEquals(LeaveStatus.PENDING, requests.find(recorded.id()).orElseThrow().status());
        assertEquals(before, dutiesOf(away), "a professor cannot move duties by confirming their own leave");
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void aProfessorMayNotRejectARequestEither() throws Exception {
        LeaveRequest recorded = recordWholeSession(someoneOnDuty());

        mvc.perform(post("/disruption/leave/" + recorded.id() + "/reject").with(csrf()))
                .andExpect(status().isForbidden());

        assertEquals(LeaveStatus.PENDING, requests.find(recorded.id()).orElseThrow().status());
    }

    @Test
    void aStrangerCannotSeeOrRecordOrDecideAnything() throws Exception {
        mvc.perform(get("/disruption")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/disruption/leave").with(csrf())
                        .param("facultyId", "F01").param("wholeSession", "yes"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/disruption/leave/1/confirm").with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertTrue(requests.all().isEmpty());
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void everyLeaveFormIsCsrfProtected() throws Exception {
        String away = someoneOnDuty();
        List<String> before = dutiesOf(away);

        mvc.perform(post("/disruption/leave").param("facultyId", away).param("wholeSession", "yes"))
                .andExpect(status().isForbidden());
        LeaveRequest recorded = recordWholeSession(away);
        mvc.perform(post("/disruption/leave/" + recorded.id() + "/confirm"))
                .andExpect(status().isForbidden());

        assertEquals(LeaveStatus.PENDING, requests.find(recorded.id()).orElseThrow().status());
        assertEquals(before, dutiesOf(away));
        assertFalse(requests.all().isEmpty(), "only the request made with a token was stored");
        assertEquals(1, requests.all().size());
    }
}
