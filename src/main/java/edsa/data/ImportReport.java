package edsa.data;

import java.util.List;

/**
 * What an upload did, or refused to do.
 *
 * <p>A failed import changes nothing: the errors name the worksheet, row and field so the
 * administrator can fix the workbook rather than guess.
 */
public record ImportReport(boolean successful,
                           int students,
                           int faculty,
                           int rooms,
                           int subjects,
                           int enrollments,
                           int exams,
                           List<String> errors) {

    public static ImportReport failed(List<String> errors) {
        return new ImportReport(false, 0, 0, 0, 0, 0, 0, List.copyOf(errors));
    }

    public static ImportReport succeeded(ImportedWorkbook workbook) {
        return new ImportReport(true,
                workbook.students().size(),
                workbook.faculty().size(),
                workbook.rooms().size(),
                workbook.subjects().size(),
                workbook.enrollmentCount(),
                workbook.exams().size(),
                List.of());
    }
}
