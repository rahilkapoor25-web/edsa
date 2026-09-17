package edsa.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edsa.core.ExamData;
import edsa.core.ExamPlan;
import edsa.core.GreedyAllocator;

import java.util.List;

import org.junit.jupiter.api.Test;

class SeatingPlanCsvTest {

    @Test
    void writesOneRowPerSeatUnderAHeader() {
        ExamData data = SampleData.load();
        ExamPlan plan = new GreedyAllocator().allocate(data);

        List<String> lines = SeatingPlanCsv.write(plan).lines().toList();

        assertEquals("slot_id,paper_code,exam_date,start_time,room_id,room_name,row,seat,student_id,student_name",
                lines.get(0));
        assertEquals(plan.seating().seatedCount() + 1, lines.size());
        assertTrue(lines.get(1).startsWith("E1,CS301,2026-11-10,09:30,R201,Seminar Hall,1,1,S001,"),
                lines.get(1));
        assertTrue(lines.stream().skip(1).allMatch(line -> line.split(",").length == 10));
    }
}
