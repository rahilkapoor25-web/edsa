package edsa.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.core.PlacedStudent;
import edsa.core.RoomSitting;
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
 * Opening one duty and seeing that room's seating.
 *
 * <p>Searching for a professor used to answer the question and then bury it under every
 * sitting in the institution. A search now shows only that professor's duties, and opening
 * one shows that exam in that room — and nothing else.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomDetailPageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @BeforeEach
    void loadTheSampleSession() {
        workspace.setData(SampleData.load());
    }

    /** The first sitting in the plan, whatever it happens to be. */
    private RoomSitting aSitting() {
        return workspace.result().plan().sittings().get(0);
    }

    // ── A search shows only that professor's duties ─────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void searchingShowsTheDutiesAndNotTheWholeInstitutionsSittings() throws Exception {
        mvc.perform(get("/rooms").param("professor", "Meera"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invigilator duties")))
                .andExpect(content().string(containsString("Dr. Meera Nair")))
                // The sittings table is what used to bury the answer.
                .andExpect(content().string(not(containsString(">Sittings<"))))
                .andExpect(content().string(not(containsString("room allocations"))));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void theSittingsTableIsStillThereWhenNobodyIsSearchedFor() throws Exception {
        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Sittings<")))
                .andExpect(content().string(not(containsString("Invigilator duties"))));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void eachDutyOffersAWayIntoThatRoom() throws Exception {
        var duty = workspace.result().plan().invigilatorsNamed("Meera").get(0).duties().get(0);

        mvc.perform(get("/rooms").param("professor", "Meera"))
                .andExpect(content().string(containsString("slot=" + duty.slot().getId())))
                .andExpect(content().string(containsString("room=" + duty.room().getId())));
    }

    // ── Opening one duty ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void openingADutyShowsThatExamInThatRoom() throws Exception {
        RoomSitting sitting = aSitting();

        mvc.perform(get("/rooms")
                        .param("slot", sitting.slot().getId())
                        .param("room", sitting.room().getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(sitting.slot().getPaperCode())))
                .andExpect(content().string(containsString(sitting.slot().getPaperName())))
                .andExpect(content().string(containsString(sitting.room().getId())))
                .andExpect(content().string(containsString(sitting.room().getBuilding())))
                .andExpect(content().string(containsString(">Seating<")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void theRoomViewShowsNoOtherSittingAndNoOtherInvigilator() throws Exception {
        RoomSitting sitting = aSitting();
        String page = mvc.perform(get("/rooms")
                        .param("slot", sitting.slot().getId())
                        .param("room", sitting.room().getId()))
                .andReturn().getResponse().getContentAsString();

        assertFalse(page.contains(">Sittings<"), "the session-wide table has no place here");
        assertFalse(page.contains("Invigilator duties"), "nor another professor's duty list");

        // No other room used in the session should be named on this page.
        for (var other : workspace.result().plan().roomsUsed()) {
            if (!other.getId().equals(sitting.room().getId())) {
                assertFalse(page.contains(">" + other.getId() + "<"),
                        "room " + other.getId() + " does not belong on this page");
            }
        }
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void everyStudentIsShownAtTheSeatTheAllocatorGaveThem() throws Exception {
        RoomSitting sitting = aSitting();
        String page = mvc.perform(get("/rooms")
                        .param("slot", sitting.slot().getId())
                        .param("room", sitting.room().getId()))
                .andReturn().getResponse().getContentAsString();

        assertFalse(sitting.students().isEmpty(), "the scenario needs somebody seated");
        for (PlacedStudent placed : sitting.students()) {
            assertTrue(page.contains(placed.student().getId()),
                    "missing student " + placed.student().getId());
            assertTrue(page.contains(placed.student().getName()),
                    "missing name " + placed.student().getName());
            assertTrue(page.contains(placed.row() + "-" + placed.column()),
                    "missing seat position " + placed.row() + "-" + placed.column());
        }
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void theGridIsTheRoomsOwnShapeAndNotInvented() throws Exception {
        RoomSitting sitting = aSitting();
        var grid = sitting.grid();

        mvc.perform(get("/rooms")
                        .param("slot", sitting.slot().getId())
                        .param("room", sitting.room().getId()))
                .andExpect(content().string(containsString("Row " + grid.rowCount())))
                .andExpect(content().string(containsString("Seat " + grid.columnCount())));

        assertEquals(sitting.room().getRowCount(), grid.rowCount());
        assertEquals(sitting.room().getColumnCount(), grid.columnCount());
        assertEquals(sitting.seated(), grid.seated());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void thereIsAWayBackToTheSearchThatLedHere() throws Exception {
        RoomSitting sitting = aSitting();

        mvc.perform(get("/rooms")
                        .param("slot", sitting.slot().getId())
                        .param("room", sitting.room().getId())
                        .param("professor", "Meera"))
                .andExpect(content().string(containsString("Back to duties")))
                .andExpect(content().string(containsString("Meera")));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void anUnknownRoomOrExamSaysSoRatherThanShowingSomethingElse() throws Exception {
        mvc.perform(get("/rooms").param("slot", "NO-SUCH-EXAM").param("room", "NO-SUCH-ROOM"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("not sat in that room")))
                .andExpect(content().string(not(containsString(">Seating<"))));
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void anExamAndARoomThatNeverMetDoNotProduceASeatingPlan() throws Exception {
        RoomSitting sitting = aSitting();

        // A room that exists but never hosts this paper. Picking it from the plan keeps the
        // test honest about what "never met" means.
        String unpairedRoom = workspace.result().plan().roomsUsed().stream()
                .map(room -> room.getId())
                .filter(id -> workspace.result().plan().sitting(sitting.slot().getId(), id).isEmpty())
                .findFirst()
                .orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(unpairedRoom != null,
                "every room in this plan hosts the first paper, so there is nothing to check");

        mvc.perform(get("/rooms")
                        .param("slot", sitting.slot().getId())
                        .param("room", unpairedRoom))
                .andExpect(content().string(containsString("not sat in that room")))
                .andExpect(content().string(not(containsString(">Seating<"))));
    }

    @Test
    void aStrangerCannotOpenARoom() throws Exception {
        RoomSitting sitting = aSitting();

        mvc.perform(get("/rooms")
                        .param("slot", sitting.slot().getId())
                        .param("room", sitting.room().getId()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "prof@thapar.edu", roles = "PROFESSOR")
    void theObviousRoomScheduleNoteIsGone() throws Exception {
        mvc.perform(get("/rooms"))
                .andExpect(content().string(not(containsString(
                        "Choose a room above to see who sits in which seat"))));
    }
}
