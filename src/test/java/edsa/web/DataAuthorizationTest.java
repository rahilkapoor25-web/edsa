package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.core.ExamData;
import edsa.core.ExamMode;
import edsa.data.SampleData;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Who may change the shared examination session, and who may only read it.
 *
 * <p>There is one workspace for the whole office: the imported data, the chosen mode and the
 * computed plan. A professor reading their duties must not be able to replace the session
 * somebody else is reading, so every endpoint that writes to it is the coordinator's. Reading
 * and downloading stay open, because that is what professors are given accounts for.
 *
 * <p>Each refusal is checked twice: the HTTP status, and that the workspace really did not
 * move.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @BeforeEach
    void loadASession() {
        workspace.setData(SampleData.load());
        workspace.setMode(ExamMode.ENDSEM);
    }

    private static MockMultipartFile anXlsx() {
        return new MockMultipartFile("workbook", "session.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1, 2, 3});
    }

    private static MockMultipartFile aCsv(String field) {
        return new MockMultipartFile(field, field + ".csv", "text/csv", "header\n".getBytes());
    }

    // ── A professor may not replace the shared session ───────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotChangeTheExamMode() throws Exception {
        mvc.perform(post("/mode").with(csrf()).param("mode", "QUIZ"))
                .andExpect(status().isForbidden());

        assertEquals(ExamMode.ENDSEM, workspace.mode(), "the mode everybody else is reading is unchanged");
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotImportAWorkbook() throws Exception {
        ExamData before = workspace.data();

        mvc.perform(multipart("/data/workbook").file(anXlsx()).with(csrf()))
                .andExpect(status().isForbidden());

        assertSame(before, workspace.data(), "an import would have replaced every stored record");
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotUploadTheCsvFiles() throws Exception {
        ExamData before = workspace.data();

        mvc.perform(multipart("/data/upload")
                        .file(aCsv("students")).file(aCsv("rooms"))
                        .file(aCsv("faculty")).file(aCsv("timetable"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertSame(before, workspace.data());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotLoadTheSampleDataOverTheRealSession() throws Exception {
        ExamData before = workspace.data();

        mvc.perform(post("/data/sample").with(csrf())).andExpect(status().isForbidden());

        assertSame(before, workspace.data());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotReloadTheStoredData() throws Exception {
        ExamData before = workspace.data();

        mvc.perform(post("/data/stored").with(csrf())).andExpect(status().isForbidden());

        assertSame(before, workspace.data());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorCannotRecomputeThePlan() throws Exception {
        var before = workspace.result();

        mvc.perform(post("/compute").with(csrf())).andExpect(status().isForbidden());

        assertSame(before, workspace.result(), "recomputing would discard the plan and any repair");
    }

    // ── But a professor may read and download ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorMayStillSeeEveryPageAndDownloadEveryExport() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/rooms")).andExpect(status().isOk());
        mvc.perform(get("/workloads")).andExpect(status().isOk());
        mvc.perform(get("/disruption")).andExpect(status().isOk());
        mvc.perform(get("/exam-plan.xlsx")).andExpect(status().isOk());
        mvc.perform(get("/seating-plan.csv")).andExpect(status().isOk());
        mvc.perform(get("/import-template.xlsx")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorIsNotShownTheFormsThatWouldBeRefused() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                // Hiding these is for clarity; the refusals above are what enforce it.
                .andExpect(content().string(Matchers.not(Matchers.containsString("/data/workbook"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Import workbook"))))
                .andExpect(content().string(Matchers.containsString("This session")));
    }

    // ── The coordinator's workflow still works ───────────────────────────────────────────

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void anAdministratorCanStillChangeTheModeAndRecompute() throws Exception {
        mvc.perform(post("/mode").with(csrf()).param("mode", "QUIZ"))
                .andExpect(status().is3xxRedirection());
        assertEquals(ExamMode.QUIZ, workspace.mode());

        mvc.perform(post("/compute").with(csrf())).andExpect(status().is3xxRedirection());
        assertTrue(workspace.hasPlan());
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void anAdministratorCanStillLoadTheSampleData() throws Exception {
        mvc.perform(post("/data/sample").with(csrf())).andExpect(status().is3xxRedirection());

        assertTrue(workspace.hasData());
        assertTrue(workspace.hasPlan(), "loading data plans it straight away, as before");
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void anAdministratorStillSeesTheImportForms() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/data/workbook")))
                .andExpect(content().string(Matchers.containsString("Import workbook")));
    }

    // ── Signed out ───────────────────────────────────────────────────────────────────────

    @Test
    void aStrangerIsSentToTheLoginPageAndChangesNothing() throws Exception {
        ExamData before = workspace.data();

        mvc.perform(post("/mode").with(csrf()).param("mode", "QUIZ"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/data/sample").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/seating-plan.csv")).andExpect(status().is3xxRedirection());

        assertSame(before, workspace.data());
        assertEquals(ExamMode.ENDSEM, workspace.mode());
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void everyDataChangingFormNeedsACsrfToken() throws Exception {
        mvc.perform(post("/mode").param("mode", "QUIZ")).andExpect(status().isForbidden());
        mvc.perform(post("/data/sample")).andExpect(status().isForbidden());
        mvc.perform(post("/compute")).andExpect(status().isForbidden());

        assertEquals(ExamMode.ENDSEM, workspace.mode());
        assertFalse(workspace.mode() == ExamMode.QUIZ);
    }
}
