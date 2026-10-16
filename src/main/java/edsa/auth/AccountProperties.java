package edsa.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** How long a provisioned account's activation link stays usable. */
@Configuration
@ConfigurationProperties(prefix = "edsa.accounts")
public class AccountProperties {

    /** A week by default: long enough for somebody on leave, short enough to expire. */
    private int activationValidHours = 168;

    public int getActivationValidHours() {
        return activationValidHours;
    }

    public void setActivationValidHours(int activationValidHours) {
        this.activationValidHours = activationValidHours;
    }
}
