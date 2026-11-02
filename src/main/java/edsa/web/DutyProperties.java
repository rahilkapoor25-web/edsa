package edsa.web;

import edsa.core.Designation;
import edsa.core.DutyPolicy;
import edsa.core.SeniorityLadder;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The faculty ladder and the duty rules, as an examination office configures them.
 *
 * <p>Configuration rather than code because none of it is this application's to decide. Which
 * ranks exist, which outrank which, how much invigilation each is asked for, whether a course
 * teacher may invigilate their own paper, whether there is a cap — published university practice
 * differs on every one of these, and an office that disagrees with a default should be able to
 * change it in a properties file rather than in a solver.
 *
 * <p>Left unset, the ladder is {@link SeniorityLadder#defaults()}: the ranks named in Thapar's
 * published recruitment and promotion policy, with duty shares this application chose and no
 * claim that they are anybody's rule.
 */
@Configuration
@EnableConfigurationProperties(DutyProperties.class)
@ConfigurationProperties(prefix = "edsa.duties")
public class DutyProperties {

    /** One configured rank. A list of these replaces the default ladder entirely. */
    public static class Rank {
        private String code;
        private String label;
        private int seniority;
        private double share = 1.0;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public int getSeniority() {
            return seniority;
        }

        public void setSeniority(int seniority) {
            this.seniority = seniority;
        }

        public double getShare() {
            return share;
        }

        public void setShare(double share) {
            this.share = share;
        }
    }

    private List<Rank> ranks = new ArrayList<>();
    private int seniorFrom = 40;
    private boolean weightBySeniority = true;
    private int maxPerMember = 0;
    private int minPerMember = 1;
    private boolean courseTeacherMayInvigilate = false;
    private boolean preferOwnDepartment = false;

    @Bean
    public SeniorityLadder seniorityLadder() {
        if (ranks.isEmpty()) {
            return new SeniorityLadder(SeniorityLadder.defaults().designations(), seniorFrom);
        }
        List<Designation> configured = ranks.stream()
                .map(rank -> new Designation(rank.getCode(), rank.getLabel(),
                        rank.getSeniority(), rank.getShare()))
                .toList();
        return new SeniorityLadder(configured, seniorFrom);
    }

    @Bean
    public DutyPolicy dutyPolicy() {
        return new DutyPolicy(weightBySeniority, maxPerMember, minPerMember,
                courseTeacherMayInvigilate, preferOwnDepartment);
    }

    public List<Rank> getRanks() {
        return ranks;
    }

    public void setRanks(List<Rank> ranks) {
        this.ranks = ranks;
    }

    public int getSeniorFrom() {
        return seniorFrom;
    }

    public void setSeniorFrom(int seniorFrom) {
        this.seniorFrom = seniorFrom;
    }

    public boolean isWeightBySeniority() {
        return weightBySeniority;
    }

    public void setWeightBySeniority(boolean weightBySeniority) {
        this.weightBySeniority = weightBySeniority;
    }

    public int getMaxPerMember() {
        return maxPerMember;
    }

    public void setMaxPerMember(int maxPerMember) {
        this.maxPerMember = maxPerMember;
    }

    public int getMinPerMember() {
        return minPerMember;
    }

    public void setMinPerMember(int minPerMember) {
        this.minPerMember = minPerMember;
    }

    public boolean isCourseTeacherMayInvigilate() {
        return courseTeacherMayInvigilate;
    }

    public void setCourseTeacherMayInvigilate(boolean courseTeacherMayInvigilate) {
        this.courseTeacherMayInvigilate = courseTeacherMayInvigilate;
    }

    public boolean isPreferOwnDepartment() {
        return preferOwnDepartment;
    }

    public void setPreferOwnDepartment(boolean preferOwnDepartment) {
        this.preferOwnDepartment = preferOwnDepartment;
    }
}
