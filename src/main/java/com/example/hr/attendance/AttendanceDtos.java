package com.example.hr.attendance;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payloads of the attendance API. None of them carries a salary or any pay figure —
 * attendance is visible to managers, pay is not.
 */
public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    /** One stored session. {@code checkOut} is null while the session is open. */
    public record SessionResponse(
            UUID id,
            UUID employeeId,
            LocalDate workDate,
            Instant checkIn,
            Instant checkOut,
            LocalDateTime localCheckIn,
            LocalDateTime localCheckOut,
            Integer minutes,
            AttendanceSource source,
            UUID correctedById,
            String correctionReason) {
    }

    /** One derived employee-day; {@code sessions} is the audit trail behind the numbers. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DayResponse(
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
            boolean openSession,
            List<SessionResponse> sessions) {
    }

    /** A range of days plus the totals over it. */
    public record AttendanceRangeResponse(
            UUID employeeId,
            String employeeName,
            LocalDate from,
            LocalDate to,
            int workingDays,
            int presentDays,
            int lateDays,
            int absentDays,
            int leaveDays,
            int unpaidLeaveDays,
            int missingCheckoutDays,
            int workedMinutes,
            int lateMinutes,
            int overtimeMinutes,
            int holidayMinutes,
            List<DayResponse> days) {
    }

    /** One line of {@code GET /api/attendance/team/today}. Never includes pay. */
    public record TeamTodayEntry(
            UUID employeeId,
            String employeeNumber,
            String employeeName,
            String jobTitle,
            AttendanceStatus status,
            LeaveCoverage leave,
            LocalDateTime firstIn,
            LocalDateTime lastOut,
            boolean currentlyIn,
            int workedMinutes,
            int lateMinutes,
            int overtimeMinutes) {
    }

    public record TeamTodayResponse(
            LocalDate day,
            DayKind dayKind,
            int teamSize,
            int inCount,
            int presentCount,
            int lateCount,
            int absentCount,
            int onLeaveCount,
            int missingCheckoutCount,
            List<TeamTodayEntry> members) {
    }

    /**
     * HR correction body. Times are instants (ISO-8601 with an offset, e.g.
     * {@code 2025-03-04T09:05:00Z}) so there is never any doubt about the zone.
     */
    public record CorrectSessionBody(
            @NotNull Instant checkIn,
            Instant checkOut,
            @NotBlank(message = "reason is required for a correction")
            @Size(max = 500, message = "reason must be at most 500 characters") String reason) {
    }
}
