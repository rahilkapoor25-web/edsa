package edsa.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
 * The invigilator search on the Room schedule page.
 *
 * <p>Driven through HTTP, so these also prove the search works as a plain form with no
 * JavaScript, and that adding it did not take the room filter away.
 *
 * <p>The bundled sample roster includes Dr. Meera Nair and Prof. Sneha Rao, which is what the
 * searches below look for.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfessorSearchPageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @BeforeEach
    void loadTheSampleSession() {
        workspace.setData(SampleData.load());
    }

    // ── The field is there and labelled ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void theSearchFieldIsOnThePageBesideTheRoomFilter() throws Exception {
        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Search professor by name...")))
                .andExpect(content().string(containsString("name=\"professor\"")))
                .andExpect(content().string(containsString("name=\"room\"")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void nothingIsSearchedUntilSomethingIsTyped() throws Exception {
        mvc.perform(get("/rooms"))
                .andExpect(content().string(not(containsString("Invigilator duties"))))
                .andExpect(content().string(containsString("Sittings")));
    }

    // ── Searching ────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aFullNameFindsTheirRooms() throws Exception {
        mvc.perform(get("/rooms").param("professor", "Dr. Meera Nair"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invigilator duties")))
                .andExpect(content().string(containsString("Dr. Meera Nair")))
                .andExpect(content().string(containsString("F01")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void partOfANameIsEnough() throws Exception {
        mvc.perform(get("/rooms").param("professor", "meera"))
                .andExpect(content().string(containsString("Dr. Meera Nair")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void capitalsAndSurroundingSpacesDoNotMatter() throws Exception {
        mvc.perform(get("/rooms").param("professor", "   SNEHA   "))
                .andExpect(content().string(containsString("Prof. Sneha Rao")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void theSearchTextStaysInTheBoxAfterSubmitting() throws Exception {
        mvc.perform(get("/rooms").param("professor", "meera"))
                .andExpect(content().string(containsString("value=\"meera\"")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void anUnknownNameSaysSoPlainly() throws Exception {
        mvc.perform(get("/rooms").param("professor", "Nobody At All"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("No matching professor or room assignments found.")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void clearingTheSearchRestoresTheOrdinaryPage() throws Exception {
        mvc.perform(get("/rooms").param("professor", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Invigilator duties"))))
                .andExpect(content().string(containsString("Sittings")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aMatchShowsTheRoomTheDateAndTheSubject() throws Exception {
        String page = mvc.perform(get("/rooms").param("professor", "Meera"))
                .andReturn().getResponse().getContentAsString();

        // Everything the duty table promises, taken from the real allocation.
        var duties = workspace.result().plan().invigilatorsNamed("Meera").get(0).duties();
        for (var duty : duties) {
            org.junit.jupiter.api.Assertions.assertTrue(page.contains(duty.room().getId()),
                    "the room " + duty.room().getId() + " should be shown");
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.contains(duty.slot().getDate().toString()),
                    "the date should be shown");
            org.junit.jupiter.api.Assertions.assertTrue(page.contains(duty.slot().getPaperCode()),
                    "the paper should be shown");
        }
        org.junit.jupiter.api.Assertions.assertFalse(duties.isEmpty(),
                "the sample roster should give Meera at least one duty");
    }

    // ── Two similar names ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void whenSeveralPeopleMatchThePageSaysHowToTellThemApart() throws Exception {
        // "Dr." matches most of the sample roster.
        mvc.perform(get("/rooms").param("professor", "Dr."))
                .andExpect(content().string(containsString("invigilators match")))
                .andExpect(content().string(containsString("Check the id and department")));
    }

    // ── The existing room filter is untouched ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void theRoomFilterStillNarrowsTheSittings() throws Exception {
        String roomId = workspace.result().plan().roomsUsed().get(0).getId();

        mvc.perform(get("/rooms").param("room", roomId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sittings")))
                // Choosing a room reveals the seat-by-seat grid, as it always did.
                .andExpect(content().string(containsString("Seat")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void theRoomFilterAndTheSearchWorkTogether() throws Exception {
        String roomId = workspace.result().plan().roomsUsed().get(0).getId();

        mvc.perform(get("/rooms").param("room", roomId).param("professor", "Meera"))
                .andExpect(status().isOk())
                // Both filters are honoured: the search results and the narrowed table.
                .andExpect(content().string(containsString("Invigilator duties")))
                .andExpect(content().string(containsString("Dr. Meera Nair")))
                .andExpect(content().string(containsString(roomId)))
                // And the room selection is remembered in the form.
                .andExpect(content().string(containsString("value=\"Meera\"")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void aClearLinkAppearsOnceEitherFilterIsUsed() throws Exception {
        mvc.perform(get("/rooms").param("professor", "Meera"))
                .andExpect(content().string(containsString("Clear")));
        mvc.perform(get("/rooms"))
                .andExpect(content().string(not(containsString(">Clear</a>"))));
    }

    // ── Privacy and access ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void theSearchResultsDoNotNameAnyStudent() throws Exception {
        String page = mvc.perform(get("/rooms").param("professor", "Meera"))
                .andReturn().getResponse().getContentAsString();

        // The searched page is now nothing but the results — the session-wide sittings table
        // is no longer rendered underneath — so the whole page is the thing to check.
        for (var student : workspace.data().students()) {
            org.junit.jupiter.api.Assertions.assertFalse(page.contains(student.getName()),
                    "a faculty search must not list students: " + student.getName());
        }
    }

    @Test
    void aStrangerCannotSearchAtAll() throws Exception {
        mvc.perform(get("/rooms").param("professor", "Meera"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void searchingBeforeAnythingIsPlannedDoesNotFail() throws Exception {
        workspace.setData(null);

        mvc.perform(get("/rooms").param("professor", "Meera"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No plan yet")));
    }
}
