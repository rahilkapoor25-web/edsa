package edsa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Who may reach the new pages and actions, enforced on the server.
 *
 * <p>Hiding a button is manners; this is the protection. Every request below is made by hand, as
 * somebody who has never seen the page, which is exactly how an unauthorised request would
 * actually arrive.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeaturePermissionTest {

    @Autowired
    private MockMvc mvc;

    // ----- timings: coordinators and administrators only ---------------------------

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotSeeOrChangeExaminationTimings() throws Exception {
        mvc.perform(get("/timings")).andExpect(status().isForbidden());
        mvc.perform(post("/timings/save").with(csrf())
                        .param("examId", "E1").param("start", "10:00"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/timings/session").with(csrf())
                        .param("date", "2026-11-10").param("start", "10:00"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/timings/revert").with(csrf()).param("examId", "E1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "coord@thapar.edu", roles = "COORDINATOR")
    void aCoordinatorMayChangeTimingsButNotAccounts() throws Exception {
        mvc.perform(get("/timings")).andExpect(status().isOk());
        mvc.perform(get("/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void anAdministratorMayChangeTimings() throws Exception {
        mvc.perform(get("/timings")).andExpect(status().isOk());
    }

    @Test
    void timingsAreNotPublic() throws Exception {
        mvc.perform(get("/timings")).andExpect(status().is3xxRedirection());
    }

    // ----- duty approval: coordinators and administrators only ---------------------

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorMaySeeTheDutiesPageButNotApproveAnything() throws Exception {
        mvc.perform(get("/duties")).andExpect(status().isOk());
        mvc.perform(post("/duties/approve").with(csrf())
                        .param("slotId", "E1").param("roomId", "R1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/duties/approve-all").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/duties/assign").with(csrf())
                        .param("slotId", "E1").param("roomId", "R1").param("facultyId", "F02"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/duties/clear").with(csrf())
                        .param("slotId", "E1").param("roomId", "R1"))
                .andExpect(status().isForbidden());
    }

    // ----- substitutions: a professor takes part, an administrator decides ----------

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotDecideOrCreditASubstitution() throws Exception {
        mvc.perform(get("/substitutions")).andExpect(status().isOk());
        mvc.perform(post("/substitutions/1/approve").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/substitutions/1/reject").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/substitutions/1/complete").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "coord@thapar.edu", roles = "COORDINATOR")
    void aCoordinatorCannotCreditASubstitutionEither() throws Exception {
        // Running the timetable is not the same as moving points about.
        mvc.perform(post("/substitutions/1/complete").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/points/adjust").with(csrf())
                        .param("facultyId", "F01").param("points", "50").param("why", "because"))
                .andExpect(status().isForbidden());
    }

    // ----- points: a professor sees their own, an administrator decides -------------

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotGrantExemptionsOrCorrectBalances() throws Exception {
        mvc.perform(get("/points")).andExpect(status().isOk());
        mvc.perform(post("/points/redemptions/1/approve").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/points/redemptions/1/reject").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/points/adjust").with(csrf())
                        .param("facultyId", "F01").param("points", "1000").param("why", "a gift"))
                .andExpect(status().isForbidden());
    }

    @Test
    void thePointsPagesAreNotPublic() throws Exception {
        mvc.perform(get("/points")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/substitutions")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/duties")).andExpect(status().is3xxRedirection());
    }

    /** CSRF is on for every state-changing request, these included. */
    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void aPostWithoutACsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/duties/approve-all")).andExpect(status().isForbidden());
        mvc.perform(post("/timings/revert").param("examId", "E1")).andExpect(status().isForbidden());
        mvc.perform(post("/substitutions/1/approve")).andExpect(status().isForbidden());
        mvc.perform(post("/points/adjust")
                        .param("facultyId", "F01").param("points", "1").param("why", "x"))
                .andExpect(status().isForbidden());
    }
}
