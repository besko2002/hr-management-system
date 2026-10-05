package com.example.hr.leave;

import com.example.hr.common.BadRequestException;
import com.example.hr.employee.Employee;
import com.example.hr.leave.LeaveDtos.LeaveCalendarEntry;
import com.example.hr.security.CurrentEmployee;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Team calendar: who of my people is off, and when.
 *
 * <p>Scope is the caller's own subtree (themself plus every descendant, resolved by the
 * Phase 1 recursive CTE); HR and ADMIN see the whole company. Only APPROVED leave appears
 * — a pending request is not yet a fact. The projection has no {@code reason} column at
 * all, so the <em>why</em> of somebody's leave can never leak through the calendar.
 */
@Service
public class LeaveCalendarService {

    private static final long MAX_RANGE_DAYS = 366;

    private final LeaveRequestRepository requests;
    private final CurrentEmployee currentEmployee;

    LeaveCalendarService(LeaveRequestRepository requests, CurrentEmployee currentEmployee) {
        this.requests = requests;
        this.currentEmployee = currentEmployee;
    }

    @Transactional(readOnly = true)
    public List<LeaveCalendarEntry> calendar(LocalDate from, LocalDate to) {
        Employee me = currentEmployee.require();
        if (to.isBefore(from)) {
            throw new BadRequestException("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BadRequestException("The calendar range must not exceed " + MAX_RANGE_DAYS + " days");
        }

        List<Object[]> rows = me.getRole().isHrOrAdmin()
                ? requests.findCompanyCalendar(from, to)
                : requests.findTeamCalendar(me.getId(), from, to);
        return rows.stream().map(LeaveMapper::toCalendarEntry).toList();
    }
}
