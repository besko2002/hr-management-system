package com.example.hr.attendance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Binds {@code app.attendance.*} into the immutable {@link AttendanceRules} and exposes the
 * pure {@link DailyAttendanceCalculator} as a bean. Values are parsed here (rather than
 * relying on {@code @Value} conversion) so a typo fails fast at startup with a clear
 * message.
 */
@Configuration
class AttendanceConfig {

    @Bean
    AttendanceRules attendanceRules(
            @Value("${app.attendance.zone:Africa/Cairo}") String zone,
            @Value("${app.attendance.work-start:09:00}") String workStart,
            @Value("${app.attendance.work-end:17:00}") String workEnd,
            @Value("${app.attendance.grace-minutes:15}") int graceMinutes,
            @Value("${app.attendance.max-overtime-minutes-per-day:240}") int maxOvertimeMinutesPerDay,
            @Value("${app.attendance.auto-close-after-hours:16}") int autoCloseAfterHours) {
        return new AttendanceRules(ZoneId.of(zone), LocalTime.parse(workStart), LocalTime.parse(workEnd),
                graceMinutes, maxOvertimeMinutesPerDay, autoCloseAfterHours);
    }

    @Bean
    DailyAttendanceCalculator dailyAttendanceCalculator(AttendanceRules rules) {
        return new DailyAttendanceCalculator(rules);
    }
}
