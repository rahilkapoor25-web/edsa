package edsa.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** How long a provisioned account's activation link stays usable. */
@Configuration
@ConfigurationProperties(prefix = "edsa.accounts")
public class AccountProperties {

    /** A week by default: long enough for somebody on leave, short enough to expire. */
    private int activationValidHours = 168;

    /**
     * Failed sign-ins tolerated from one address, or one caller, before a short pause.
     *
     * <p>Ten is well above anybody's honest fumbling and far below what guessing a password
     * needs.
     */
    private int loginMaxAttempts = 10;

    /** How long the pause lasts. Deliberately short — see {@code LoginAttempts}. */
    private int loginCooldownSeconds = 60;

    /** Failures older than this are forgotten, so an honest user is never carrying a tally. */
    private int loginWindowSeconds = 300;

    public int getActivationValidHours() {
        return activationValidHours;
    }

    public void setActivationValidHours(int activationValidHours) {
        this.activationValidHours = activationValidHours;
    }

    public int getLoginMaxAttempts() {
        return loginMaxAttempts;
    }

    public void setLoginMaxAttempts(int loginMaxAttempts) {
        this.loginMaxAttempts = loginMaxAttempts;
    }

    public int getLoginCooldownSeconds() {
        return loginCooldownSeconds;
    }

    public void setLoginCooldownSeconds(int loginCooldownSeconds) {
        this.loginCooldownSeconds = loginCooldownSeconds;
    }

    public int getLoginWindowSeconds() {
        return loginWindowSeconds;
    }

    public void setLoginWindowSeconds(int loginWindowSeconds) {
        this.loginWindowSeconds = loginWindowSeconds;
    }
}
