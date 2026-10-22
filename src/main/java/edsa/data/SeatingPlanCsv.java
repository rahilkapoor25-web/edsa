package edsa.data;

import edsa.core.ExamPlan;
import edsa.core.PlacedStudent;
import edsa.core.RoomSitting;

/**
 * Writes a finished seating plan out as one row per seat.
 *
 * <p>Every field that came from an uploaded workbook is treated as untrusted. A spreadsheet
 * evaluates a cell beginning {@code =}, {@code +}, {@code -} or {@code @} as a formula when it
 * opens a CSV, so a student recorded as {@code =HYPERLINK("http://...")} would run as one on
 * the coordinator's machine rather than being read as a name. Such a value is prefixed with an
 * apostrophe, which is what tells a spreadsheet to treat the rest as text.
 */
public final class SeatingPlanCsv {

    private static final String HEADER =
            "slot_id,paper_code,exam_date,start_time,room_id,room_name,row,seat,student_id,student_name";

    /**
     * The characters a spreadsheet reads as the start of a formula, plus the two control
     * characters it strips before deciding — a leading tab or carriage return would otherwise
     * hide a formula from this check.
     */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

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

    /** One untrusted value, made safe to read as text and correctly escaped for CSV. */
    static String field(String value) {
        String text = defused(value == null ? "" : value);
        return needsQuoting(text) ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }

    /** Stops a spreadsheet reading the value as a formula. An ordinary name is untouched. */
    private static String defused(String value) {
        if (value.isEmpty() || FORMULA_STARTERS.indexOf(value.charAt(0)) < 0) {
            return value;
        }
        return "'" + value;
    }

    /**
     * A field needs quoting when it holds a separator, a quote, or a line break — without this
     * a name containing a newline would split one seat across two rows.
     */
    private static boolean needsQuoting(String value) {
        return value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
    }
}
