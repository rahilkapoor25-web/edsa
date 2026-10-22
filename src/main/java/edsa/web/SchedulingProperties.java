package edsa.web;

import edsa.core.ExamPlanner;
import edsa.core.SchedulingConfig;

import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Operating hours and capacity assumptions, configurable without touching the scheduler. */
@Configuration
@EnableConfigurationProperties(SchedulingProperties.class)
@ConfigurationProperties(prefix = "edsa.scheduling")
public class SchedulingProperties {

    private LocalTime dayStart = LocalTime.of(9, 0);
    private LocalTime dayEnd = LocalTime.of(18, 0);
    private int turnaroundMinutes = 30;
    private int fallbackRoomCapacity = 30;

    public void setDayStart(LocalTime dayStart) {
        this.dayStart = dayStart;
    }

    public void setDayEnd(LocalTime dayEnd) {
        this.dayEnd = dayEnd;
    }

    public void setTurnaroundMinutes(int turnaroundMinutes) {
        this.turnaroundMinutes = turnaroundMinutes;
    }

    public void setFallbackRoomCapacity(int fallbackRoomCapacity) {
        this.fallbackRoomCapacity = fallbackRoomCapacity;
    }

    public LocalTime getDayStart() {
        return dayStart;
    }

    public LocalTime getDayEnd() {
        return dayEnd;
    }

    public int getTurnaroundMinutes() {
        return turnaroundMinutes;
    }

    public int getFallbackRoomCapacity() {
        return fallbackRoomCapacity;
    }

    @Bean
    public SchedulingConfig schedulingConfig() {
        return new SchedulingConfig(dayStart, dayEnd, turnaroundMinutes, fallbackRoomCapacity);
    }

    @Bean
    public ExamPlanner examPlanner(SchedulingConfig config) {
        return new ExamPlanner(config);
    }
}
