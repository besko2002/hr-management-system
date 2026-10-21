package com.example.hr.leave;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Counts working days in an inclusive date range. Pure: no database, no clock, no Spring —
 * the weekend definition and the holiday dates are handed in, which is what makes it
 * exhaustively unit-testable.
 *
 * <p>The weekend is configurable because it is regional: the default for Egypt is
 * {@code FRIDAY,SATURDAY} (see {@code app.leave.weekend-days}). A holiday that falls on a
 * weekend day is simply still not a working day — it is never counted twice.
 *
 * <p>Half days are deliberately not supported: a working day is 1 or 0.
 */
public final class WorkingDayCalculator {

    private final Set<DayOfWeek> weekendDays;

    public WorkingDayCalculator(Collection<DayOfWeek> weekendDays) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (weekendDays != null) {
            days.addAll(weekendDays);
        }
        this.weekendDays = Collections.unmodifiableSet(days);
    }

    public Set<DayOfWeek> weekendDays() {
        return weekendDays;
    }

    public boolean isWeekend(LocalDate day) {
        return weekendDays.contains(day.getDayOfWeek());
    }

    public boolean isWorkingDay(LocalDate day, Collection<LocalDate> holidays) {
        return !isWeekend(day) && !asSet(holidays).contains(day);
    }

    /**
     * Number of working days between {@code start} and {@code end}, both inclusive.
     * Returns 0 when the range is empty (end before start) or contains nothing but
     * weekend days and holidays.
     */
    public int workingDays(LocalDate start, LocalDate end, Collection<LocalDate> holidays) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        Set<LocalDate> holidaySet = asSet(holidays);
        int days = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (!isWeekend(day) && !holidaySet.contains(day)) {
                days++;
            }
        }
        return days;
    }

    /**
     * The last date of a range that starts at {@code start} and contains exactly
     * {@code workingDays} working days. Used by tests and clients to turn "5 days off"
     * into a concrete end date.
     */
    public LocalDate endDateFor(LocalDate start, int workingDays, Collection<LocalDate> holidays) {
        if (workingDays < 1) {
            throw new IllegalArgumentException("workingDays must be at least 1");
        }
        Set<LocalDate> holidaySet = asSet(holidays);
        LocalDate day = start;
        int counted = 0;
        LocalDate last = start;
        while (counted < workingDays) {
            if (!isWeekend(day) && !holidaySet.contains(day)) {
                counted++;
                last = day;
            }
            day = day.plusDays(1);
        }
        return last;
    }

    /** The first working day on or after {@code from}. */
    public LocalDate nextWorkingDay(LocalDate from, Collection<LocalDate> holidays) {
        Set<LocalDate> holidaySet = asSet(holidays);
        LocalDate day = from;
        while (isWeekend(day) || holidaySet.contains(day)) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static Set<LocalDate> asSet(Collection<LocalDate> holidays) {
        if (holidays == null || holidays.isEmpty()) {
            return Set.of();
        }
        return holidays instanceof Set<LocalDate> set ? set : new HashSet<>(holidays);
    }
}
