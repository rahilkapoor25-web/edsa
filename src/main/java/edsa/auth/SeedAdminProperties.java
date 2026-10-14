package edsa.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * An optional first account, created on start-up only when the account table is empty.
 *
 * <p>There is no built-in default password: when {@code edsa.seed-admin.password} is unset,
 * nothing is created and the first account must be made deliberately. Supply it through an
 * environment variable (EDSA_SEEDADMIN_PASSWORD) for a development machine, never in a
 * committed properties file.
 */
@ConfigurationProperties(prefix = "edsa.seed-admin")
public class SeedAdminProperties {

    private boolean enabled = false;
    private String email;
    private String password;
    private String displayName = "Administrator";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isUsable() {
        return enabled && email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
