package edsa.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.data.SampleData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The guards on the forms that replace data or take a while.
 *
 * <p>These check the markup carries the attributes the layout's script acts on. The script
 * itself is progressive enhancement — the forms submit perfectly well without it — so what is
 * worth asserting is that the destructive actions are marked as destructive.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormSafetyTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @BeforeEach
    void loadASession() {
        workspace.setData(SampleData.load());
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void importingAsksBeforeItReplacesEverything() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Importing replaces every stored student")))
                .andExpect(content().string(containsString("data-busy=\"Importing...\"")));
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void recomputingWarnsThatItDiscardsAbsenceCover() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("discards any absence cover")));
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void loadingTheSampleDataAsksFirst() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString(
                        "replaces the session currently loaded with the sample data")));
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void confirmingAnAbsenceAsksBecauseItMovesDuties() throws Exception {
        mvc.perform(get("/disruption"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-busy=\"Recording...\"")));
    }

    @Test
    @WithMockUser(username = "boss@thapar.edu", roles = "ADMIN")
    void theDuplicateSubmitGuardIsLoadedOnEveryPage() throws Exception {
        for (String page : new String[] {"/", "/rooms", "/workloads", "/disruption"}) {
            mvc.perform(get(page))
                    .andExpect(status().isOk())
                    // The layout's one inline script. It only ever enhances: the attribute it
                    // reads is absent until a form is actually submitted.
                    .andExpect(content().string(containsString("Progressive enhancement")))
                    .andExpect(content().string(containsString("form.dataset.submitted")));
        }
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aProfessorSeesNoDestructiveFormsToBeWarnedAbout() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("Importing replaces every stored student"))));
    }
}
