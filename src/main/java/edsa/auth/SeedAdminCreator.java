package edsa.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes sure the configured administrator can sign in, every time the application starts.
 *
 * <p>Nothing happens unless an email and a password were supplied, so a deployment that
 * configures neither starts with no accounts rather than with a known one. The password is
 * never logged.
 *
 * <p>When they <em>are</em> supplied, the account is created if it is missing and otherwise
 * has its password reset and is re-enabled. The old behaviour — create only while the account
 * table was completely empty — quietly did nothing once any account existed, which left no way
 * to add an administrator after professors had been provisioned, or to recover one whose
 * password had been lost. With a database that now survives restarts that would have been
 * permanent rather than merely annoying.
 *
 * <p>This is a development and recovery convenience, which is why the documentation says to
 * leave these settings unset in a real deployment: anybody who can set an environment variable
 * on the server could otherwise take the administrator account over.
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
                    log.warn("No accounts exist and no administrator is configured. Set "
                            + "edsa.seed-admin.enabled=true with an email and password to create one.");
                }
                return;
            }
            try {
                AccountService.AdminOutcome outcome = accounts.provisionAdministrator(
                        properties.getEmail(), properties.getPassword(), properties.getDisplayName());
                if (outcome == AccountService.AdminOutcome.CREATED) {
                    log.info("Created the administrator account for {}", properties.getEmail());
                } else {
                    log.info("Reset the password for the existing administrator account {}",
                            properties.getEmail());
                }
            } catch (IllegalArgumentException refused) {
                // The message names what is wrong with the address or the length of the
                // password. It never contains the password itself.
                log.warn("The configured administrator was not set up: {}", refused.getMessage());
            }
        };
    }
}
