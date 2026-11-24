package edsa.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.core.AbsenceWindow;
import edsa.core.LeaveRequest;
import edsa.data.SampleData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The duty points: earned only by a confirmed absence, and seen only by an administrator. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PointsPageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @Autowired
    private LeaveWorkflow leave;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSessionAndNoPoints() {
        jdbc.update("DELETE FROM duty_point");
        jdbc.update("DELETE FROM leave_request");
        workspace.setData(SampleData.load());
    }

    private int pointsOf(String facultyId) {
        Integer total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(points), 0) FROM duty_point WHERE faculty_id = ?", Integer.class, facultyId);
        return total == null ? 0 : total;
    }

    private LeaveRequest absenceOf(String facultyId) {
        return leave.record(facultyId, AbsenceWindow.wholeSession(), "Reported sick", leave.defaultDeadline());
    }

    @Test
    void confirmingAnAbsenceTakesAPointPerDutyMissedAndGivesOnePerDutyCovered() {
        int dutiesOfF01 = workspace.result().plan().duties().forFaculty("F01").size();

        leave.confirmAndReassign(absenceOf("F01").id(), "boss@thapar.edu");

        assertEquals(-dutiesOfF01, pointsOf("F01"), "one point per duty the absence took");
        assertEquals(dutiesOfF01, jdbc.queryForObject(
                "SELECT COALESCE(SUM(points), 0) FROM duty_point WHERE points > 0", Integer.class),
                "one point to whoever covered each of them");
    }

    @Test
    void aRejectedOrPendingAbsenceEarnsNoPoints() {
        absenceOf("F02");
        leave.reject(absenceOf("F01").id(), "boss@thapar.edu");

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM duty_point", Integer.class));
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void anAdministratorSeesEveryTeacherAndTheirPoints() throws Exception {
        leave.confirmAndReassign(absenceOf("F01").id(), "boss@thapar.edu");

        mvc.perform(get("/points"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dr. Meera Nair")))
                .andExpect(content().string(containsString("Prof. Arun Sethi")))
                .andExpect(content().string(containsString("−1")))
                .andExpect(content().string(containsString("+1")))
                .andExpect(content().string(containsString("Covered")));
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void anAdministratorHasAPointsTab() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("href=\"/points\"")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotOpenThePointsPage() throws Exception {
        mvc.perform(get("/points")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorIsNotShownThePointsTab() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(not(containsString("href=\"/points\""))));
    }
}
