package com.example.hr.payroll;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response payloads of the payroll API. */
public final class PayrollDtos {

    private PayrollDtos() {
    }

    /** POST /api/payroll/runs */
    public record CreateRunBody(
            @NotNull @Min(2000) @Max(2100) Integer year,
            @NotNull @Min(1) @Max(12) Integer month) {
    }

    public record PayrollRunResponse(
            UUID id,
            int year,
            int month,
            PayrollRunStatus status,
            int payslipCount,
            UUID createdById,
            Instant createdAt,
            UUID finalizedById,
            Instant finalizedAt) {
    }

    /** Company-level sums of one run; every figure is a BigDecimal at scale 2. */
    public record PayrollTotals(
            int employees,
            BigDecimal baseSalary,
            BigDecimal proratedBase,
            BigDecimal overtimePay,
            BigDecimal holidayPay,
            BigDecimal unpaidLeaveDeduction,
            BigDecimal absenceDeduction,
            BigDecimal grossEarnings,
            BigDecimal insurance,
            BigDecimal tax,
            BigDecimal netPay) {
    }

    public record PayrollRunDetailResponse(
            PayrollRunResponse run,
            PayrollTotals totals,
            List<PayslipResponse> payslips) {
    }

    /**
     * Every line item of one payslip, so the net can be re-derived by hand from the
     * response alone. Never returned to a manager — only to the employee themself (and
     * only once the run is FINALIZED) and to HR/ADMIN.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PayslipResponse(
            UUID id,
            UUID runId,
            int year,
            int month,
            PayrollRunStatus runStatus,
            UUID employeeId,
            String employeeNumber,
            String employeeName,
            String jobTitle,
            String departmentName,
            LocalDate hireDate,
            LocalDate terminatedAt,

            BigDecimal baseSalary,
            int workingDaysInMonth,
            int payableWorkingDays,
            int unpaidLeaveDays,
            int absentDays,
            int lateMinutes,
            int overtimeMinutes,
            int holidayMinutes,

            BigDecimal dailyRate,
            BigDecimal hourlyRate,
            BigDecimal overtimeHours,
            BigDecimal holidayHours,

            BigDecimal proratedBase,
            BigDecimal overtimePay,
            BigDecimal holidayPay,
            BigDecimal unpaidLeaveDeduction,
            BigDecimal absenceDeduction,

            BigDecimal grossEarnings,
            BigDecimal insurableWage,
            BigDecimal insurance,
            BigDecimal personalExemption,
            BigDecimal taxableIncome,
            BigDecimal tax,
            BigDecimal netPay,
            boolean netFloored,
            String warning,

            List<TaxBracketCharge> taxBreakdown,
            PayrollRates ratesUsed,
            Instant createdAt) {
    }
}
