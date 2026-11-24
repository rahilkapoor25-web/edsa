package edsa.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.core.ExamMode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The pages, driven as a signed-in administrator would drive them. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExamWorkflowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void theDataPageOffersEveryModeWithItsDuration() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quiz")))
                .andExpect(content().string(containsString("10 min")))
                .andExpect(content().string(containsString("2 h")))
                .andExpect(content().string(containsString("3 h")));
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void loadingDataSchedulesItStraightAway() throws Exception {
        mvc.perform(post("/data/sample").with(csrf())).andExpect(redirectedUrl("/"));

        assertTrue(workspace.hasPlan(), "the plan is built on import, without pressing compute");
        assertFalse(workspace.result().plan().schedule().isEmpty());
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void choosingAModeRetimesTheTimetableImmediately() throws Exception {
        mvc.perform(post("/data/sample").with(csrf())).andExpect(redirectedUrl("/"));

        mvc.perform(post("/mode").param("mode", "QUIZ").with(csrf())).andExpect(redirectedUrl("/"));
        assertEquals(ExamMode.QUIZ, workspace.mode());
        assertEquals(10, workspace.result().plan().schedule().get(0).getDurationMinutes());

        mvc.perform(post("/mode").param("mode", "ENDSEM").with(csrf())).andExpect(redirectedUrl("/"));
        assertEquals(180, workspace.result().plan().schedule().get(0).getDurationMinutes(),
                "the mode change re-times the plan rather than leaving a stale one");
        assertEquals("3 h", workspace.result().plan().schedule().get(0).getDurationLabel());
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void aStateChangingPostWithoutACsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/data/sample")).andExpect(status().isForbidden());
        mvc.perform(post("/compute")).andExpect(status().isForbidden());
        mvc.perform(post("/mode").param("mode", "QUIZ")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void theTemplateDownloadsAsAnXlsxWorkbook() throws Exception {
        mvc.perform(get("/import-template.xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("edsa-import-template.xlsx")))
                .andExpect(header().string("Content-Type",
                        containsString("spreadsheetml.sheet")));
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void uploadingAWorkbookThroughThePageImportsIt() throws Exception {
        byte[] workbook = edsa.data.ExcelTemplate.write();
        MockMultipartFile upload = new MockMultipartFile("workbook", "session.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        mvc.perform(multipart("/data/workbook").file(upload).with(csrf()))
                .andExpect(redirectedUrl("/"));

        assertTrue(workspace.hasData());
        assertEquals(3, workspace.data().students().size());
        // The page says which workbook is in use, so a reload never looks like it vanished.
        mvc.perform(get("/")).andExpect(content().string(containsString("session.xlsx")));
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void theModeIsLockedUntilAWorkbookHasBeenImported() throws Exception {
        workspace.setData(null);

        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Generate a schedule first")));

        workspace.setData(edsa.data.SampleData.load());

        mvc.perform(get("/"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Generate a schedule first"))));
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void checkingAWorkbookReportsItIsReadyWithoutLoadingIt() throws Exception {
        MockMultipartFile upload = new MockMultipartFile("workbook", "session.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", edsa.data.ExcelTemplate.write());
        var before = workspace.data();

        mvc.perform(multipart("/data/workbook/check").file(upload).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(true))
                .andExpect(jsonPath("$.students").value(3))
                .andExpect(jsonPath("$.exams").value(3));

        assertTrue(before == workspace.data(), "a check must not load or plan anything");
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void checkingSomethingThatIsNotAWorkbookSaysWhy() throws Exception {
        MockMultipartFile upload = new MockMultipartFile("workbook", "notes.txt", "text/plain", "hello".getBytes());

        mvc.perform(multipart("/data/workbook/check").file(upload).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(false))
                .andExpect(jsonPath("$.errors[0]").value(containsString(".xlsx")));
    }

    @Test
    @WithMockUser(username = "admin@thapar.edu", roles = "ADMIN")
    void theRoomScheduleAndWorkloadPagesOpenBeforeAnythingIsComputed() throws Exception {
        mvc.perform(get("/rooms")).andExpect(status().isOk());
        mvc.perform(get("/workloads")).andExpect(status().isOk());
    }
}
