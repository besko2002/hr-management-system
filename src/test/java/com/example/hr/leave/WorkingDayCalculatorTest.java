package com.example.hr.leave;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the working-day arithmetic: no Spring, no database, no clock.
 *
 * <p>Reference week: Monday 2025-03-03 … Sunday 2025-03-09. With the Egyptian default
 * weekend (Friday + Saturday) that week holds five working days — Sunday is a working day.
 */
class WorkingDayCalculatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 3, 3);
    private static final LocalDate FRIDAY = LocalDate.of(2025, 3, 7);
    private static final LocalDate SATURDAY = LocalDate.of(2025, 3, 8);
    private static final LocalDate SUNDAY = LocalDate.of(2025, 3, 9);

    private static final WorkingDayCalculator EGYPT =
            new WorkingDayCalculator(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));

    @Test
    void theReferenceDatesAreTheWeekdaysTheTestsAssume() {
        assertThat(MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(FRIDAY.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(SATURDAY.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(SUNDAY.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    void aSingleWorkingDayCountsAsOne() {
        assertThat(EGYPT.workingDays(MONDAY, MONDAY, List.of())).isEqualTo(1);
    }

    @Test
    void mondayToThursdayIsFourWorkingDays() {
        assertThat(EGYPT.workingDays(MONDAY, MONDAY.plusDays(3), List.of())).isEqualTo(4);
    }

    @Test
    void aFullCalendarWeekHasFiveWorkingDaysWithAnEgyptianWeekend() {
        assertThat(EGYPT.workingDays(MONDAY, SUNDAY, List.of())).isEqualTo(5);
    }

    @Test
    void fridayAndSaturdayAreWeekend() {
        assertThat(EGYPT.isWeekend(FRIDAY)).isTrue();
        assertThat(EGYPT.isWeekend(SATURDAY)).isTrue();
        assertThat(EGYPT.isWeekend(SUNDAY)).isFalse();
        assertThat(EGYPT.isWeekend(MONDAY)).isFalse();
    }

    @Test
    void aRangeOfOnlyWeekendDaysHasNoWorkingDays() {
        assertThat(EGYPT.workingDays(FRIDAY, SATURDAY, List.of())).isZero();
    }

    @Test
    void aHolidayIsNotAWorkingDay() {
        assertThat(EGYPT.workingDays(MONDAY, MONDAY.plusDays(3), List.of(MONDAY.plusDays(1)))).isEqualTo(3);
        assertThat(EGYPT.isWorkingDay(MONDAY, List.of(MONDAY))).isFalse();
        assertThat(EGYPT.isWorkingDay(MONDAY, List.of())).isTrue();
    }

    @Test
    void severalHolidaysInsideTheRangeAreAllExcluded() {
        assertThat(EGYPT.workingDays(MONDAY, SUNDAY, List.of(MONDAY, MONDAY.plusDays(2)))).isEqualTo(3);
    }

    @Test
    void aHolidayFallingOnAWeekendDayIsNotSubtractedTwice() {
        int withoutHoliday = EGYPT.workingDays(MONDAY, SUNDAY, List.of());
        int withHolidayOnFriday = EGYPT.workingDays(MONDAY, SUNDAY, List.of(FRIDAY, SATURDAY));
        assertThat(withHolidayOnFriday).isEqualTo(withoutHoliday).isEqualTo(5);
    }

    @Test
    void holidaysOutsideTheRangeAreIgnored() {
        assertThat(EGYPT.workingDays(MONDAY, MONDAY.plusDays(3), List.of(LocalDate.of(2025, 4, 1)))).isEqualTo(4);
    }

    @Test
    void aRangeAcrossTheYearEndIsCountedNormally() {
        // Mon 2025-12-29 … Sun 2026-01-04: 29, 30, 31, Jan 1 and Jan 4 are working days.
        assertThat(EGYPT.workingDays(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4), List.of())).isEqualTo(5);
    }

    @Test
    void aRangeAcrossTheYearEndRespectsHolidaysOnBothSides() {
        assertThat(EGYPT.workingDays(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4),
                List.of(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)))).isEqualTo(3);
    }

    @Test
    void anEndBeforeTheStartHasNoWorkingDays() {
        assertThat(EGYPT.workingDays(MONDAY, MONDAY.minusDays(1), List.of())).isZero();
    }

    @Test
    void nullBoundsOrNullHolidaysAreTolerated() {
        assertThat(EGYPT.workingDays(null, MONDAY, List.of())).isZero();
        assertThat(EGYPT.workingDays(MONDAY, null, List.of())).isZero();
        assertThat(EGYPT.workingDays(MONDAY, MONDAY, null)).isEqualTo(1);
    }

    @Test
    void theWeekendIsConfigurable() {
        WorkingDayCalculator western =
                new WorkingDayCalculator(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        assertThat(western.workingDays(MONDAY, SUNDAY, List.of())).isEqualTo(5);
        assertThat(western.isWeekend(FRIDAY)).isFalse();
        assertThat(western.isWeekend(SUNDAY)).isTrue();
        assertThat(western.weekendDays()).containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    @Test
    void aSingleDayWeekendLeavesSixWorkingDaysAWeek() {
        WorkingDayCalculator sundayOff = new WorkingDayCalculator(Set.of(DayOfWeek.SUNDAY));
        assertThat(sundayOff.workingDays(MONDAY, SUNDAY, List.of())).isEqualTo(6);
    }

    @Test
    void withoutAnyWeekendEveryCalendarDayCounts() {
        WorkingDayCalculator alwaysOpen = new WorkingDayCalculator(Set.of());
        assertThat(alwaysOpen.workingDays(MONDAY, SUNDAY, List.of())).isEqualTo(7);
        assertThat(alwaysOpen.weekendDays()).isEmpty();
    }

    @Test
    void aNullWeekendConfigurationMeansNoWeekendAtAll() {
        assertThat(new WorkingDayCalculator(null).workingDays(MONDAY, SUNDAY, List.of())).isEqualTo(7);
    }

    @Test
    void aWholeMonthIsCountedWithoutOffByOneErrors() {
        // March 2025 has 31 days, 5 Fridays (7, 14, 21, 28) ... exactly 4 Fridays and 5 Saturdays.
        int days = EGYPT.workingDays(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), List.of());
        assertThat(days).isEqualTo(31 - 4 - 5);
    }

    @Test
    void endDateForTurnsAWorkingDayCountIntoAnInclusiveEndDate() {
        // Five working days from Monday run to the following Sunday: Fri and Sat are skipped.
        assertThat(EGYPT.endDateFor(MONDAY, 5, List.of())).isEqualTo(SUNDAY);
        assertThat(EGYPT.endDateFor(MONDAY, 1, List.of())).isEqualTo(MONDAY);
        assertThat(EGYPT.workingDays(MONDAY, EGYPT.endDateFor(MONDAY, 5, List.of()), List.of())).isEqualTo(5);
    }

    @Test
    void endDateForSkipsHolidaysToo() {
        assertThat(EGYPT.endDateFor(MONDAY, 2, List.of(MONDAY.plusDays(1)))).isEqualTo(MONDAY.plusDays(2));
    }

    @Test
    void endDateForRefusesANonPositiveCount() {
        assertThatThrownBy(() -> EGYPT.endDateFor(MONDAY, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextWorkingDaySkipsWeekendAndHolidays() {
        assertThat(EGYPT.nextWorkingDay(FRIDAY, List.of())).isEqualTo(SUNDAY);
        assertThat(EGYPT.nextWorkingDay(MONDAY, List.of())).isEqualTo(MONDAY);
        assertThat(EGYPT.nextWorkingDay(FRIDAY, List.of(SUNDAY))).isEqualTo(SUNDAY.plusDays(1));
    }
}
