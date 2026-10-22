package edsa.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates an uploaded workbook and, only when every sheet and row is sound, replaces the
 * stored reference data with it.
 *
 * <p>Parsing happens before anything is written, and the write runs in one transaction, so a
 * workbook that fails validation cannot leave the database partly updated.
 */
@Service
public class WorkbookImportService {

    /** Twelve megabytes is far more than a session's worth of rows and far less than a bomb. */
    public static final long MAX_UPLOAD_BYTES = 12L * 1024 * 1024;

    private final ExamDataRepository repository;

    public WorkbookImportService(ExamDataRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportReport importWorkbook(String fileName, long sizeInBytes, InputStream content) {
        if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
            return ImportReport.failed(List.of("Only .xlsx workbooks can be uploaded"));
        }
        if (sizeInBytes <= 0) {
            return ImportReport.failed(List.of("The uploaded file is empty"));
        }
        if (sizeInBytes > MAX_UPLOAD_BYTES) {
            return ImportReport.failed(List.of("The workbook is larger than "
                    + (MAX_UPLOAD_BYTES / (1024 * 1024)) + " MB"));
        }

        ExcelWorkbookReader reader = new ExcelWorkbookReader();
        Optional<ImportedWorkbook> parsed;
        try (InputStream source = content) {
            parsed = reader.read(source);
        } catch (IOException e) {
            return ImportReport.failed(List.of("The upload could not be read: " + e.getMessage()));
        }

        if (parsed.isEmpty()) {
            return ImportReport.failed(reader.errors());
        }
        ImportedWorkbook workbook = parsed.get();
        if (workbook.exams().isEmpty()) {
            return ImportReport.failed(List.of("The Exams worksheet has no rows, so there is nothing to schedule"));
        }

        repository.replaceAll(workbook);
        return ImportReport.succeeded(workbook);
    }
}
