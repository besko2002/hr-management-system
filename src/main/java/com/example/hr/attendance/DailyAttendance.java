package com.example.hr.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Everything derived for one employee-day. All of it is computed, nothing is stored.
 *
 * @param workedMinutes   sum of the <em>closed</em> sessions; an open one contributes nothing
 * @param lateMinutes     reported only — lateness is never deducted from pay
 * @param overtimeMinutes already capped at {@code app.attendance.max-overtime-minutes-per-day}
 * @param openSession     true while a session of this day has no check-out yet
 */
public record DailyAttendance(
        LocalDate day,
        DayKind dayKind,
        AttendanceStatus status,
        LeaveCoverage leave,
        LocalDateTime firstIn,
        LocalDateTime lastOut,
        int sessionCount,
        int workedMinutes,
        int lateMinutes,
        int overtimeMinutes,
        boolean openSession) {

    /** Overtime earned at the holiday/weekend multiplier rather than the weekday one. */
    public int restDayMinutes() {
        return dayKind.isRestDay() ? overtimeMinutes : 0;
    }

    /** Overtime earned at the weekday overtime multiplier. */
    public int weekdayOvertimeMinutes() {
        return dayKind.isRestDay() ? 0 : overtimeMinutes;
    }

    public boolean isUnpaidLeaveDay() {
        return status == AttendanceStatus.ON_LEAVE && leave == LeaveCoverage.UNPAID;
    }

    public boolean isAbsentDay() {
        return status == AttendanceStatus.ABSENT;
    }
}
