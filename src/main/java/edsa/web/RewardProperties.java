package edsa.web;

import edsa.core.RewardRules;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What a substitute duty earns and what being excused one costs.
 *
 * <p>The ratio between the two is the scheme's whole character, so it is configuration and not
 * a constant: an office that wants covering a duty to be worth more, or exemptions to be dearer,
 * changes it here. The defaults charge three covers for one exemption, which keeps the scheme a
 * reward for extra work rather than a way of doing less of it.
 */
@Configuration
@EnableConfigurationProperties(RewardProperties.class)
@ConfigurationProperties(prefix = "edsa.rewards")
public class RewardProperties {

    private int pointsPerSubstitution = 10;
    private int pointsToSkipOneDuty = 30;
    private int validityDays = 365;
    private int maxRedemptionsPerSession = 1;

    @Bean
    public RewardRules rewardRules() {
        return new RewardRules(pointsPerSubstitution, pointsToSkipOneDuty,
                validityDays, maxRedemptionsPerSession);
    }

    public int getPointsPerSubstitution() {
        return pointsPerSubstitution;
    }

    public void setPointsPerSubstitution(int pointsPerSubstitution) {
        this.pointsPerSubstitution = pointsPerSubstitution;
    }

    public int getPointsToSkipOneDuty() {
        return pointsToSkipOneDuty;
    }

    public void setPointsToSkipOneDuty(int pointsToSkipOneDuty) {
        this.pointsToSkipOneDuty = pointsToSkipOneDuty;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(int validityDays) {
        this.validityDays = validityDays;
    }

    public int getMaxRedemptionsPerSession() {
        return maxRedemptionsPerSession;
    }

    public void setMaxRedemptionsPerSession(int maxRedemptionsPerSession) {
        this.maxRedemptionsPerSession = maxRedemptionsPerSession;
    }
}
