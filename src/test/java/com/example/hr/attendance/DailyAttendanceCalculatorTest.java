package com.example.hr.attendance;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the per-day attendance derivation: no Spring, no database, no clock.
 * The reference day is a Monday (2025-03-03) and the rules are the shipped defaults
 * (09:00–17:00, 15 minutes of grace, 240 minutes of overtime a day, abandoned after 16h).
 */
class DailyAttendanceCalculatorTest {

    private static final AttendanceRules RULES = new AttendanceRules(
            ZoneId.of("Africa/Cairo"), LocalTime.of(9, 0), LocalTime.of(17, 0), 15, 240, 16);

    private static final DailyAttendanceCalculator CALCULATOR = new DailyAttendanceCalculator(RULES);

    /** Monday. */
    private static final LocalDate MONDAY = LocalDate.of(2025, 3, 3);

    /** Friday — a weekend day with the default FRIDAY,SATURDAY weekend. */
    private static final LocalDate FRIDAY = LocalDate.of(2025, 3, 7);

    private static DailyAttendance workingDay(SessionWindow... sessions) {
        return CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING, LeaveCoverage.NONE,
                List.of(sessions), MONDAY.atTime(23, 59)));
    }

    // ------------------------------------------------------------------ lateness

    @Test
    void aFullNineToFiveDayIsPresentWithNoLatenessAndNoOvertime() {
        DailyAttendance day = workingDay(SessionWindow.of(MONDAY, "09:00", "17:00"));

        assertThat(day.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(day.workedMinutes()).isEqualTo(480);
        assertThat(day.lateMinutes()).isZero();
        assertThat(day.overtimeMinutes()).isZero();
        assertThat(day.firstIn()).isEqualTo(MONDAY.atTime(9, 0));
        assertThat(day.lastOut()).isEqualTo(MONDAY.atTime(17, 0));
        assertThat(day.sessionCount()).isEqualTo(1);
        assertThat(day.openSession()).isFalse();
    }

    @Test
    void arrivingExactlyOnTheGraceBoundaryIsNotLate() {
        DailyAttendance day = workingDay(SessionWindow.of(MONDAY, "09:15", "17:00"));

        assertThat(day.lateMinutes()).isZero();
        assertThat(day.status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void oneMinutePastTheGraceBoundaryIsOneMinuteLate() {
        DailyAttendance day = workingDay(SessionWindow.of(MONDAY, "09:16", "17:00"));

        assertThat(day.lateMinutes()).isEqualTo(1);
        assertThat(day.status()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void latenessIsMeasuredFromTheGraceBoundaryNotFromWorkStart() {
        DailyAttendance day = workingDay(SessionWindow.of(MONDAY, "10:00", "17:00"));

        assertThat(day.lateMinutes()).isEqualTo(45);
        assertThat(day.status()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void arrivingEarlyIsNeverNegativeLateness() {
        DailyAttendance day = workingDay(SessionWindow.of(MONDAY, "07:30", "17:00"));

        assertThat(day.lateMinutes()).isZero();
        assertThat(day.workedMinutes()).isEqualTo(570);
        assertThat(day.overtimeMinutes()).isZero();
    }

    @Test
    void latenessIsOnlyComputedOnWorkingDays() {
        DailyAttendance weekend = CALCULATOR.calculate(new DailyAttendanceInput(FRIDAY, DayKind.WEEKEND,
                LeaveCoverage.NONE, List.of(SessionWindow.of(FRIDAY, "15:00", "17:00")),
                FRIDAY.atTime(23, 59)));

        assertThat(weekend.lateMinutes()).isZero();
        assertThat(weekend.status()).isEqualTo(AttendanceStatus.WEEKEND);
    }

    // ------------------------------------------------------------------ overtime

    @Test
    void onlyTheMinutesAfterWorkEndCountAsOvertime() {
        DailyAttendance day = workingDay(SessionWindow.of(MONDAY, "09:00", "19:00"));

        assertThat(day.workedMinutes()).isEqualTo(600);
        assertThat(day.overtimeMinutes()).isEqualTo(120);
        assertThat(day.weekdayOvertimeMinutes()).isEqualTo(120);
        assertThat(day.restDayMinutes()).isZero();
    }

    @Test
    void aSessionStartingAfterWorkEndIsOvertimeInFull() {
        DailyAttendance day = workingDay(SessionWindow.of(MONDAY, "18:00", "20:00"));

        assertThat(day.workedMinutes()).isEqualTo(120);
        assertThat(day.overtimeMinutes()).isEqualTo(120);
        // 09:15 -> 18:00 is 8h45m of lateness; the day is still LATE, oddly but honestly.
        assertThat(day.lateMinutes()).isEqualTo(525);
        assertThat(day.status()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void overtimeIsCappedAtTheConfiguredDailyMaximum() {
        DailyAttendance day = workingDay(SessionWindow.of(MONDAY, "09:00", "23:00"));

        assertThat(day.workedMinutes()).isEqualTo(840);
        assertThat(day.overtimeMinutes()).isEqualTo(240);
    }

    @Test
    void overtimeExactlyAtTheCapIsNotReduced() {
        DailyAttendance day = workingDay(SessionWindow.of(MONDAY, "09:00", "21:00"));

        assertThat(day.overtimeMinutes()).isEqualTo(240);
    }

    // ------------------------------------------------------------------ several sessions

    @Test
    void severalSessionsAreSummedAndTheirEdgesReported() {
        DailyAttendance day = workingDay(
                SessionWindow.of(MONDAY, "09:00", "12:00"),
                SessionWindow.of(MONDAY, "13:00", "17:30"));

        assertThat(day.sessionCount()).isEqualTo(2);
        assertThat(day.workedMinutes()).isEqualTo(180 + 270);
        assertThat(day.firstIn()).isEqualTo(MONDAY.atTime(9, 0));
        assertThat(day.lastOut()).isEqualTo(MONDAY.atTime(17, 30));
        assertThat(day.overtimeMinutes()).isEqualTo(30);
        assertThat(day.status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void sessionsHandedInOutOfOrderStillYieldTheEarliestFirstIn() {
        DailyAttendance day = workingDay(
                SessionWindow.of(MONDAY, "13:00", "17:00"),
                SessionWindow.of(MONDAY, "09:05", "12:00"));

        assertThat(day.firstIn()).isEqualTo(MONDAY.atTime(9, 5));
        assertThat(day.lastOut()).isEqualTo(MONDAY.atTime(17, 0));
        assertThat(day.lateMinutes()).isZero();
    }

    @Test
    void overtimeOfSeveralSessionsIsSummedBeforeTheCapIsApplied() {
        DailyAttendance day = workingDay(
                SessionWindow.of(MONDAY, "09:00", "17:00"),
                SessionWindow.of(MONDAY, "18:00", "21:00"),
                SessionWindow.of(MONDAY, "21:30", "23:30"));

        assertThat(day.workedMinutes()).isEqualTo(480 + 180 + 120);
        assertThat(day.overtimeMinutes()).isEqualTo(240);
    }

    // ------------------------------------------------------------------ weekends and holidays

    @Test
    void everyMinuteWorkedOnAWeekendIsOvertimeAtTheHolidayRate() {
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(FRIDAY, DayKind.WEEKEND,
                LeaveCoverage.NONE, List.of(SessionWindow.of(FRIDAY, "10:00", "14:00")),
                FRIDAY.atTime(23, 59)));

        assertThat(day.status()).isEqualTo(AttendanceStatus.WEEKEND);
        assertThat(day.workedMinutes()).isEqualTo(240);
        assertThat(day.overtimeMinutes()).isEqualTo(240);
        assertThat(day.restDayMinutes()).isEqualTo(240);
        assertThat(day.weekdayOvertimeMinutes()).isZero();
    }

    @Test
    void holidayWorkIsRecordedAsHolidayAndCountsEntirelyAsOvertime() {
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.HOLIDAY,
                LeaveCoverage.NONE, List.of(SessionWindow.of(MONDAY, "09:00", "13:00")),
                MONDAY.atTime(23, 59)));

        assertThat(day.status()).isEqualTo(AttendanceStatus.HOLIDAY);
        assertThat(day.workedMinutes()).isEqualTo(240);
        assertThat(day.restDayMinutes()).isEqualTo(240);
    }

    @Test
    void restDayOvertimeIsCappedToo() {
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(FRIDAY, DayKind.WEEKEND,
                LeaveCoverage.NONE, List.of(SessionWindow.of(FRIDAY, "06:00", "18:00")),
                FRIDAY.atTime(23, 59)));

        assertThat(day.workedMinutes()).isEqualTo(720);
        assertThat(day.overtimeMinutes()).isEqualTo(240);
    }

    @Test
    void anEmptyWeekendIsSimplyAWeekend() {
        DailyAttendance day = CALCULATOR.calculate(DailyAttendanceInput.of(FRIDAY, DayKind.WEEKEND));

        assertThat(day.status()).isEqualTo(AttendanceStatus.WEEKEND);
        assertThat(day.workedMinutes()).isZero();
        assertThat(day.firstIn()).isNull();
    }

    @Test
    void anEmptyHolidayIsSimplyAHoliday() {
        assertThat(CALCULATOR.calculate(DailyAttendanceInput.of(MONDAY, DayKind.HOLIDAY)).status())
                .isEqualTo(AttendanceStatus.HOLIDAY);
    }

    @Test
    void aHolidayCoveredByLeaveIsStillAHolidayNotLeave() {
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.HOLIDAY,
                LeaveCoverage.PAID, List.of(), MONDAY.atTime(23, 59)));

        assertThat(day.status()).isEqualTo(AttendanceStatus.HOLIDAY);
    }

    // ------------------------------------------------------------------ absence and leave

    @Test
    void aWorkingDayWithNoSessionAndNoLeaveIsAbsent() {
        DailyAttendance day = CALCULATOR.calculate(DailyAttendanceInput.of(MONDAY, DayKind.WORKING));

        assertThat(day.status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(day.isAbsentDay()).isTrue();
        assertThat(day.isUnpaidLeaveDay()).isFalse();
    }

    @Test
    void aWorkingDayCoveredByPaidLeaveIsOnLeaveAndCostsNothing() {
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING,
                LeaveCoverage.PAID, List.of(), MONDAY.atTime(23, 59)));

        assertThat(day.status()).isEqualTo(AttendanceStatus.ON_LEAVE);
        assertThat(day.leave()).isEqualTo(LeaveCoverage.PAID);
        assertThat(day.isUnpaidLeaveDay()).isFalse();
        assertThat(day.isAbsentDay()).isFalse();
    }

    @Test
    void anUnpaidLeaveDayStaysDistinguishableFromAPaidOne() {
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING,
                LeaveCoverage.UNPAID, List.of(), MONDAY.atTime(23, 59)));

        assertThat(day.status()).isEqualTo(AttendanceStatus.ON_LEAVE);
        assertThat(day.isUnpaidLeaveDay()).isTrue();
    }

    @Test
    void workingOnAnApprovedLeaveDayCountsAsWorkedAndIsNotDeducted() {
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING,
                LeaveCoverage.UNPAID, List.of(SessionWindow.of(MONDAY, "09:00", "17:00")),
                MONDAY.atTime(23, 59)));

        assertThat(day.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(day.leave()).isEqualTo(LeaveCoverage.UNPAID);
        assertThat(day.isUnpaidLeaveDay()).isFalse();
        assertThat(day.workedMinutes()).isEqualTo(480);
    }

    // ------------------------------------------------------------------ open sessions

    @Test
    void anOpenSessionOnTheCurrentDayMeansTheEmployeeIsSimplyStillIn() {
        LocalDateTime now = MONDAY.atTime(11, 0);
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING,
                LeaveCoverage.NONE, List.of(SessionWindow.of(MONDAY, "09:00", null)), now));

        assertThat(day.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(day.openSession()).isTrue();
        assertThat(day.workedMinutes()).isZero();
        assertThat(day.lastOut()).isNull();
    }

    @Test
    void anOpenSessionOlderThanTheAutoCloseWindowIsAMissingCheckout() {
        LocalDateTime now = MONDAY.plusDays(1).atTime(9, 0);
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING,
                LeaveCoverage.NONE, List.of(SessionWindow.of(MONDAY, "09:00", null)), now));

        assertThat(day.status()).isEqualTo(AttendanceStatus.MISSING_CHECKOUT);
        assertThat(day.workedMinutes()).isZero();
        assertThat(day.overtimeMinutes()).isZero();
        assertThat(day.lateMinutes()).isZero();
        assertThat(day.openSession()).isTrue();
    }

    @Test
    void theAutoCloseWindowBoundaryIsInclusive() {
        // 16 hours exactly after a 09:00 check-in.
        LocalDateTime justAbandoned = MONDAY.atTime(1, 0).plusDays(1);
        assertThat(CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING,
                LeaveCoverage.NONE, List.of(SessionWindow.of(MONDAY, "09:00", null)), justAbandoned))
                .status()).isEqualTo(AttendanceStatus.MISSING_CHECKOUT);

        LocalDateTime oneMinuteEarlier = justAbandoned.minusMinutes(1);
        assertThat(CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING,
                LeaveCoverage.NONE, List.of(SessionWindow.of(MONDAY, "09:00", null)), oneMinuteEarlier))
                .status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void aMissingCheckoutDiscardsEvenTheClosedSessionsOfThatDay() {
        LocalDateTime now = MONDAY.plusDays(2).atTime(9, 0);
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING,
                LeaveCoverage.NONE,
                List.of(SessionWindow.of(MONDAY, "09:00", "12:00"), SessionWindow.of(MONDAY, "13:00", null)),
                now));

        assertThat(day.status()).isEqualTo(AttendanceStatus.MISSING_CHECKOUT);
        assertThat(day.sessionCount()).isEqualTo(2);
        assertThat(day.workedMinutes()).isZero();
        assertThat(day.firstIn()).isEqualTo(MONDAY.atTime(9, 0));
        assertThat(day.lastOut()).isEqualTo(MONDAY.atTime(12, 0));
    }

    @Test
    void aClosedAndAStillOpenSessionOnTheSameDayKeepTheClosedMinutes() {
        LocalDateTime now = MONDAY.atTime(14, 0);
        DailyAttendance day = CALCULATOR.calculate(new DailyAttendanceInput(MONDAY, DayKind.WORKING,
                LeaveCoverage.NONE,
                List.of(SessionWindow.of(MONDAY, "09:00", "12:00"), SessionWindow.of(MONDAY, "13:00", null)),
                now));

        assertThat(day.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(day.workedMinutes()).isEqualTo(180);
        assertThat(day.openSession()).isTrue();
    }

    // ------------------------------------------------------------------ guards

    @Test
    void aSessionCannotEndBeforeItStarts() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> SessionWindow.of(MONDAY, "17:00", "09:00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workEndMustBeAfterWorkStart() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> new AttendanceRules(
                        ZoneId.of("UTC"), LocalTime.of(17, 0), LocalTime.of(9, 0), 0, 0, 16)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theLateThresholdIsWorkStartPlusGrace() {
        assertThat(RULES.lateThreshold()).isEqualTo(LocalTime.of(9, 15));
    }
}
