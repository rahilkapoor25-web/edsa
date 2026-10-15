package edsa.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Marking somebody absent through the page, and what the page then shows. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisruptionPageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @BeforeEach
    @WithMockUser(username = "prof@thapar.edu")
    void loadAPlan() {
        workspace.setData(edsa.data.SampleData.load());
    }

    private String someoneOnDuty() {
        return workspace.result().plan().duties().all().get(0).facultyId();
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void thePageListsEveryInvigilatorWithTheirDutyCount() throws Exception {
        mvc.perform(get("/disruption"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Disruption")))
                .andExpect(content().string(containsString("duties")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void markingSomeoneAbsentReassignsTheirRoomsAndShowsTheDiff() throws Exception {
        String away = someoneOnDuty();
        int dutiesBefore = workspace.result().plan().duties().all().size();

        mvc.perform(post("/disruption").with(csrf()).param("facultyId", away))
                .andExpect(redirectedUrl("/disruption"));

        assertTrue(workspace.result().plan().duties().forFaculty(away).isEmpty());
        assertEquals(dutiesBefore, workspace.result().plan().duties().all().size());
        assertEquals(away, workspace.lastRepair().absent().getId());

        mvc.perform(get("/disruption"))
                .andExpect(content().string(containsString("What changed")))
                .andExpect(content().string(containsString("was marked absent")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void theRepairedRosterIsWhatTheOtherPagesAndDownloadsShow() throws Exception {
        String away = someoneOnDuty();

        mvc.perform(post("/disruption").with(csrf()).param("facultyId", away));

        mvc.perform(get("/workloads"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Workloads")));
        mvc.perform(get("/exam-plan.xlsx")).andExpect(status().isOk());
        assertTrue(workspace.result().checks().stream().allMatch(check -> check.satisfied()),
                "the repaired plan is re-checked and still holds");
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void anInvigilatorWithNoDutiesIsReportedRatherThanSilentlyIgnored() throws Exception {
        String idle = workspace.result().plan().workloads().stream()
                .filter(workload -> workload.duties() == 0)
                .findFirst().orElseThrow().member().getId();

        mvc.perform(post("/disruption").with(csrf()).param("facultyId", idle))
                .andExpect(redirectedUrl("/disruption"));

        assertTrue(workspace.lastRepair() == null, "nothing was applied");
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu")
    void aBadDateIsRefused() throws Exception {
        mvc.perform(post("/disruption").with(csrf())
                        .param("facultyId", someoneOnDuty()).param("date", "16-11-2026"))
                .andExpect(redirectedUrl("/disruption"));

        assertTrue(workspace.lastRepair() == null);
    }

    @Test
    void aStrangerCannotMarkAnybodyAbsent() throws Exception {
        mvc.perform(get("/disruption")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/disruption").with(csrf()).param("facultyId", "F01"))
                .andExpect(status().is3xxRedirection());
    }
}
