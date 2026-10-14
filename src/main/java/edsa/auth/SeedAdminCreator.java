package edsa.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the first administrator when one is configured and no account exists yet.
 *
 * <p>Nothing happens unless an email and password were supplied, so a deployment that
 * configures neither starts with no accounts rather than with a known one. The password is
 * never logged.
 */
@Configuration
@EnableConfigurationProperties(SeedAdminProperties.class)
public class SeedAdminCreator {

    private static final Logger log = LoggerFactory.getLogger(SeedAdminCreator.class);

    @Bean
    ApplicationRunner seedFirstAdministrator(SeedAdminProperties properties, AccountService accounts) {
        return args -> {
            if (!properties.isUsable()) {
                if (accounts.accountCount() == 0) {
                    log.warn("No accounts exist and no seed administrator is configured. "
                            + "Set edsa.seed-admin.enabled=true with an email and password to create one.");
                }
                return;
            }
            if (accounts.accountCount() > 0) {
                log.info("Accounts already exist; the configured seed administrator was not created.");
                return;
            }
            accounts.register(properties.getEmail(), properties.getPassword(),
                    properties.getDisplayName(), "ADMIN");
            log.info("Created the seed administrator account for {}", properties.getEmail());
        };
    }
}
