package com.example.hr.payroll;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every number of one payslip, including all intermediates, so the figure at the bottom
 * can be audited line by line:
 *
 * <pre>
 *   dailyRate      = baseSalary / workingDaysInMonth
 *   hourlyRate     = dailyRate / workingHoursPerDay
 *   proratedBase   = baseSalary × payableWorkingDays / workingDaysInMonth
 *   overtimePay    = overtimeHours × hourlyRate × overtimeMultiplier
 *   holidayPay     = holidayHours  × hourlyRate × holidayOvertimeMultiplier
 *   grossEarnings  = proratedBase + overtimePay + holidayPay
 *                    − unpaidLeaveDeduction − absenceDeduction
 *   insurance      = clamp(baseSalary, min, max) × insuranceEmployeeRate
 *   taxableIncome  = max(0, grossEarnings − insurance − personalExemption)
 *   tax            = Σ bracket charges (marginal)
 *   netPay         = max(0, grossEarnings − insurance − tax)
 * </pre>
 */
public record PayrollResult(
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
        List<TaxBracketCharge> taxBreakdown,

        BigDecimal netPay,
        /** True when the arithmetic produced a negative net and it was floored to 0.00. */
        boolean netFloored,
        PayrollRates rates) {

    /** Total deducted before the statutory items: unpaid leave plus absence. */
    public BigDecimal totalAttendanceDeductions() {
        return Money.scaled(unpaidLeaveDeduction.add(absenceDeduction));
    }

    /** Insurance plus tax. */
    public BigDecimal totalStatutoryDeductions() {
        return Money.scaled(insurance.add(tax));
    }
}
