package com.example.hr.attendance;

/**
 * The derived state of one employee-day. Never stored: {@link DailyAttendanceCalculator}
 * recomputes it from the sessions, the calendar and the approved leave every time it is
 * asked, so an HR correction is reflected immediately and no row can go stale.
 */
public enum AttendanceStatus {

    /** A working day with at least one session, arrived within the grace period. */
    PRESENT,

    /** A working day with at least one session, first check-in after workStart + grace. */
    LATE,

    /** A working day with no session and no approved leave covering it. */
    ABSENT,

    /** A working day covered by an APPROVED leave request (paid or unpaid). */
    ON_LEAVE,

    /** A public holiday. Work done on it is still recorded and paid at the holiday rate. */
    HOLIDAY,

    /** A configured weekend day. Same treatment as a holiday for pay purposes. */
    WEEKEND,

    /**
     * A past day whose session was never closed. It contributes no worked minutes and no
     * overtime; HR has to correct it. An <em>open session on the current day</em> is not
     * this — the employee is simply still in, and the day reads PRESENT/LATE.
     */
    MISSING_CHECKOUT;

    public boolean isWorkedDay() {
        return this == PRESENT || this == LATE;
    }
}
