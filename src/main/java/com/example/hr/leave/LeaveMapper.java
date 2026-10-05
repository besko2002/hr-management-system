package com.example.hr.leave;

import com.example.hr.employee.Employee;
import com.example.hr.leave.LeaveDtos.LeaveCalendarEntry;
import com.example.hr.leave.LeaveDtos.LeaveRequestResponse;
import com.example.hr.leave.LeaveDtos.LeaveTypeResponse;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/** Entity to DTO mapping for the leave module. Call inside a transaction (lazy employee). */
public final class LeaveMapper {

    private LeaveMapper() {
    }

    public static LeaveTypeResponse toResponse(LeaveTypeDefinition definition) {
        return new LeaveTypeResponse(definition.getCode(), definition.isPaid(), definition.isRequiresBalance(),
                definition.getAnnualAllowanceDays());
    }

    public static LeaveRequestResponse toResponse(LeaveRequest request) {
        Employee employee = request.getEmployee();
        Employee decider = request.getDecidedBy();
        return new LeaveRequestResponse(
                request.getId(), employee.getId(), employee.getFullName(), request.getLeaveType(),
                request.getStartDate(), request.getEndDate(), request.getLeaveYear(), request.getWorkingDays(),
                request.getReason(), request.getStatus(),
                decider == null ? null : decider.getId(),
                decider == null ? null : decider.getFullName(),
                request.getDecidedAt(), request.getDecisionNote(), request.getCreatedAt());
    }

    /**
     * Row layout of the calendar queries in {@link LeaveRequestRepository}: request id,
     * employee id, full name, leave type, start date, end date, working days. No reason.
     */
    public static LeaveCalendarEntry toCalendarEntry(Object[] row) {
        return new LeaveCalendarEntry(
                (UUID) row[0], (UUID) row[1], (String) row[2], LeaveType.valueOf((String) row[3]),
                toLocalDate(row[4]), toLocalDate(row[5]), ((Number) row[6]).intValue());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return ((Date) value).toLocalDate();
    }
}
