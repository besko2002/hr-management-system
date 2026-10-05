package com.example.hr.leave;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.DayOfWeek;
import java.util.Set;

/** Wiring for the leave module: the weekend definition and the clock services read "today" from. */
@Configuration
class LeaveConfig {

    /**
     * The weekend is regional, so it is configuration, not code:
     * {@code app.leave.weekend-days}, default {@code FRIDAY,SATURDAY} (Egypt).
     */
    @Bean
    WorkingDayCalculator workingDayCalculator(
            @Value("${app.leave.weekend-days:FRIDAY,SATURDAY}") Set<DayOfWeek> weekendDays) {
        return new WorkingDayCalculator(weekendDays);
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
