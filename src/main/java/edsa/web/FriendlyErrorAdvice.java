package edsa.web;

import edsa.data.WorkbookImportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Turns the failures a coordinator can actually cause into a sentence they can act on.
 *
 * <p>An upload larger than the configured limit is rejected by the servlet container before
 * any controller runs, which without this produced a stack trace page. The detail still goes
 * to the log for whoever maintains the application; what reaches the screen says what to do
 * about it.
 *
 * <p>Deliberately narrow. Everything else keeps going to the error page, rather than being
 * swallowed here and reported as something it is not.
 */
@ControllerAdvice
public class FriendlyErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(FriendlyErrorAdvice.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(MaxUploadSizeExceededException tooLarge, RedirectAttributes flash) {
        log.info("An upload was refused for exceeding the configured size limit");
        flash.addFlashAttribute("error", "That file is larger than "
                + (WorkbookImportService.MAX_UPLOAD_BYTES / (1024 * 1024))
                + " MB, so it was not uploaded. Nothing has been changed.");
        return "redirect:/";
    }
}
