package com.example.hr.attendance;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * The attendance policy, immutable and handed to the pure calculator so that tests can
 * vary it without touching Spring. Bound from {@code app.attendance.*} by
 * {@link AttendanceConfig}.
 *
 * @param zone                     the zone every instant is rendered in to get a work day
 *                                 and a wall-clock time; attendance is a local-time concept
 * @param workStart                nominal start of the working day (09:00)
 * @param workEnd                  nominal end of the working day (17:00)
 * @param graceMinutes             minutes after {@code workStart} that are not yet late
 * @param maxOvertimeMinutesPerDay cap on the overtime credited for a single day
 * @param autoCloseAfterHours      a session still open this many hours after its check-in
 *                                 is considered abandoned and is reported as
 *                                 MISSING_CHECKOUT instead of "still in"
 */
public record AttendanceRules(
        ZoneId zone,
        LocalTime workStart,
        LocalTime workEnd,
        int graceMinutes,
        int maxOvertimeMinutesPerDay,
        int autoCloseAfterHours) {

    public AttendanceRules {
        if (zone == null || workStart == null || workEnd == null) {
            throw new IllegalArgumentException("zone, workStart and workEnd are required");
        }
        if (!workEnd.isAfter(workStart)) {
            throw new IllegalArgumentException("app.attendance.work-end must be after work-start");
        }
        if (graceMinutes < 0 || maxOvertimeMinutesPerDay < 0 || autoCloseAfterHours < 1) {
            throw new IllegalArgumentException("graceMinutes/maxOvertimeMinutesPerDay must be >= 0 "
                    + "and autoCloseAfterHours >= 1");
        }
    }

    /** The first instant of the day that already counts as late. */
    public LocalTime lateThreshold() {
        return workStart.plusMinutes(graceMinutes);
    }
}
