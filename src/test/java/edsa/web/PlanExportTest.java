package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edsa.data.SampleData;

import java.io.ByteArrayInputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The plan downloads as a workbook a professor can circulate.
 *
 * <p>The session is loaded straight into the workspace rather than through {@code /data/sample},
 * because importing is the coordinator's job while downloading is not: this is a test about the
 * download, and loading it directly also stops it depending on what another test class left
 * behind in the shared workspace.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanExportTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Workspace workspace;

    @Test
    @WithMockUser(username = "professor@thapar.edu", roles = "PROFESSOR")
    void theDownloadHasATimetableMatrixSeatingAndDutiesSheet() throws Exception {
        workspace.setData(SampleData.load());

        byte[] body = mvc.perform(get("/exam-plan.xlsx"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            assertEquals(4, book.getNumberOfSheets(), "Timetable, Seating Matrix, Seating, Duties");
            assertTrue(book.getSheet("Timetable").getLastRowNum() > 0);
            assertTrue(book.getSheet("Seating Matrix").getLastRowNum() > 0);
            assertTrue(book.getSheet("Seating").getLastRowNum() > 0);
            assertTrue(book.getSheet("Duties").getLastRowNum() > 0);
            assertEquals("Exam", book.getSheet("Timetable").getRow(0).getCell(0).getStringCellValue());
            assertEquals("Student", book.getSheet("Seating").getRow(0).getCell(10).getStringCellValue());
        }
    }

    @Test
    @WithMockUser(username = "professor@thapar.edu", roles = "PROFESSOR")
    void theDownloadIsRefusedBeforeAnythingIsPlanned() throws Exception {
        workspace.setData(null);

        mvc.perform(get("/exam-plan.xlsx")).andExpect(status().isNotFound());
    }
}
