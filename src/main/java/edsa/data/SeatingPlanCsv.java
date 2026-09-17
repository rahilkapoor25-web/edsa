package edsa.data;

import edsa.core.ExamPlan;
import edsa.core.PlacedStudent;
import edsa.core.RoomSitting;

/** Writes a finished seating plan out as one row per seat. */
public final class SeatingPlanCsv {

    private static final String HEADER =
            "slot_id,paper_code,exam_date,start_time,room_id,room_name,row,seat,student_id,student_name";

    private SeatingPlanCsv() {
    }

    public static String write(ExamPlan plan) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (RoomSitting sitting : plan.sittings()) {
            for (PlacedStudent placed : sitting.students()) {
                csv.append(field(sitting.slot().getId())).append(',')
                        .append(field(sitting.slot().getPaperCode())).append(',')
                        .append(sitting.slot().getDate()).append(',')
                        .append(sitting.slot().getStartTime()).append(',')
                        .append(field(sitting.room().getId())).append(',')
                        .append(field(sitting.room().getName())).append(',')
                        .append(placed.row()).append(',')
                        .append(placed.column()).append(',')
                        .append(field(placed.student().getId())).append(',')
                        .append(field(placed.student().getName()))
                        .append('\n');
            }
        }
        return csv.toString();
    }

    private static String field(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
