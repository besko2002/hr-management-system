package com.example.hr.leave;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request and response payloads of the leave API. */
public final class LeaveDtos {

    private LeaveDtos() {
    }

    /** Seeded leave-type reference data (HR/ADMIN). */
    public record LeaveTypeResponse(
            LeaveType code,
            boolean paid,
            boolean requiresBalance,
            int annualAllowanceDays) {
    }

    /**
     * One balance row. {@code remainingDays} is computed
     * ({@code entitled + carriedOver - used - pending}) and is {@code null} for types that
     * do not consume a balance, such as UNPAID.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LeaveBalanceResponse(
            LeaveType leaveType,
            int year,
            boolean paid,
            boolean requiresBalance,
            int entitledDays,
            int carriedOverDays,
            int usedDays,
            int pendingDays,
            Integer remainingDays) {
    }

    /** POST /api/leave/requests */
    public record CreateLeaveRequestBody(
            @NotNull LeaveType type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500, message = "reason must be at most 500 characters") String reason) {
    }

    /** Body of approve / reject / cancel. A rejection must carry a note. */
    public record DecisionBody(
            @Size(max = 500, message = "decisionNote must be at most 500 characters") String decisionNote) {
    }

    public record LeaveRequestResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            LeaveType leaveType,
            LocalDate startDate,
            LocalDate endDate,
            int year,
            int workingDays,
            String reason,
            LeaveStatus status,
            UUID decidedById,
            String decidedByName,
            Instant decidedAt,
            String decisionNote,
            Instant createdAt) {
    }

    /** Team-calendar entry. Deliberately has no {@code reason} field at all. */
    public record LeaveCalendarEntry(
            UUID requestId,
            UUID employeeId,
            String employeeName,
            LeaveType leaveType,
            LocalDate startDate,
            LocalDate endDate,
            int workingDays) {
    }

    public record HolidayRequestBody(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 120) String name) {
    }

    public record HolidayResponse(
            UUID id,
            LocalDate date,
            String name) {
    }

    /** Result of one accrual run; {@code employeesCredited} is 0 on a repeated run. */
    public record AccrualRunResponse(
            int year,
            int month,
            LeaveType leaveType,
            int daysPerEmployee,
            int employeesConsidered,
            int employeesCredited,
            int employeesAlreadyCredited) {
    }
}
