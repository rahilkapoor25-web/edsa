package edsa.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * How long a leave request may sit unreviewed before it is flagged.
 *
 * <p>The deadline is only ever a prompt to review. Nothing happens automatically when it
 * passes: an unreviewed request stays pending and the duty roster stays as it was.
 */
@Configuration
@ConfigurationProperties(prefix = "edsa.leave")
public class LeaveProperties {

    /** Hours allowed for review when the person recording the absence does not set a deadline. */
    private int confirmWithinHours = 24;

    public int getConfirmWithinHours() {
        return confirmWithinHours;
    }

    public void setConfirmWithinHours(int confirmWithinHours) {
        this.confirmWithinHours = confirmWithinHours;
    }
}
