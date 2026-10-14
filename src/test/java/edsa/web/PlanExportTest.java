package edsa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The plan downloads as a workbook a professor can circulate. */
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
    void theDownloadHasATimetableSeatingAndDutiesSheet() throws Exception {
        mvc.perform(post("/data/sample").with(csrf()));

        byte[] body = mvc.perform(get("/exam-plan.xlsx"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            assertEquals(3, book.getNumberOfSheets());
            assertTrue(book.getSheet("Timetable").getLastRowNum() > 0);
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
