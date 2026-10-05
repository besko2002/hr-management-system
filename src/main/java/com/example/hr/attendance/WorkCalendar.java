package com.example.hr.attendance;

import com.example.hr.leave.HolidayRepository;
import com.example.hr.leave.LeaveRequestRepository;
import com.example.hr.leave.WorkingDayCalculator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The company calendar as attendance and payroll need it: what kind of day a date is, and
 * which days of a range are covered by somebody's APPROVED leave.
 *
 * <p>It is a thin, cached-per-call adapter over the Phase 2 building blocks — the
 * configurable weekend of {@link WorkingDayCalculator} and the {@code holidays} table — so
 * that there is exactly one definition of "working day" in the whole application.
 */
@Component
public class WorkCalendar {

    private final WorkingDayCalculator workingDays;
    private final HolidayRepository holidays;
    private final LeaveRequestRepository leaveRequests;

    WorkCalendar(WorkingDayCalculator workingDays, HolidayRepository holidays,
                 LeaveRequestRepository leaveRequests) {
        this.workingDays = workingDays;
        this.holidays = holidays;
        this.leaveRequests = leaveRequests;
    }

    /** The holiday dates of an inclusive range, loaded once per request. */
    @Transactional(readOnly = true)
    public Set<LocalDate> holidaysBetween(LocalDate from, LocalDate to) {
        return new HashSet<>(holidays.findDatesBetween(from, to));
    }

    public DayKind kindOf(LocalDate day, Set<LocalDate> holidayDates) {
        if (workingDays.isWeekend(day)) {
            return DayKind.WEEKEND;
        }
        return holidayDates.contains(day) ? DayKind.HOLIDAY : DayKind.WORKING;
    }

    /** Working days of an inclusive range, using the same rule as the leave module. */
    public int workingDaysBetween(LocalDate from, LocalDate to, Set<LocalDate> holidayDates) {
        return workingDays.workingDays(from, to, holidayDates);
    }

    /**
     * Per employee, per day: is that day covered by an APPROVED leave request, and is it
     * paid? One query for the whole set of employees and the whole range. When two
     * requests cover the same day (impossible through the API — overlaps are refused — but
     * cheap to be safe about) UNPAID wins, because that is the version that costs the
     * employee money and must not be silently dropped.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Map<LocalDate, LeaveCoverage>> leaveCoverage(Collection<UUID> employeeIds,
                                                                  LocalDate from, LocalDate to) {
        Map<UUID, Map<LocalDate, LeaveCoverage>> byEmployee = new HashMap<>();
        if (employeeIds.isEmpty()) {
            return byEmployee;
        }
        List<Object[]> rows = leaveRequests.findApprovedCoverage(employeeIds, from, to);
        for (Object[] row : rows) {
            UUID employeeId = (UUID) row[0];
            LocalDate start = (LocalDate) row[1];
            LocalDate end = (LocalDate) row[2];
            LeaveCoverage coverage = Boolean.TRUE.equals(row[3]) ? LeaveCoverage.PAID : LeaveCoverage.UNPAID;
            Map<LocalDate, LeaveCoverage> days = byEmployee.computeIfAbsent(employeeId, key -> new HashMap<>());
            LocalDate day = start.isBefore(from) ? from : start;
            LocalDate last = end.isAfter(to) ? to : end;
            for (; !day.isAfter(last); day = day.plusDays(1)) {
                LeaveCoverage existing = days.get(day);
                days.put(day, existing == LeaveCoverage.UNPAID ? LeaveCoverage.UNPAID : coverage);
            }
        }
        return byEmployee;
    }
}
