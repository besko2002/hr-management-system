package com.example.hr.payroll;

import java.math.BigDecimal;

/**
 * The complete input of one payslip: the employee's monthly gross base, the calendar
 * counts, the attendance aggregates and the rates in force. Immutable, DB-free and
 * clock-free, which is what lets {@link PayrollCalculator} be unit-tested against
 * hand-computed figures.
 *
 * @param baseSalary         the employee's <b>monthly</b> gross base salary
 * @param workingDaysInMonth working days of the whole month (weekend + holidays excluded)
 * @param payableWorkingDays working days inside the employment window of that month —
 *                           equal to {@code workingDaysInMonth} for a full month, smaller
 *                           for a mid-month hire or termination
 * @param unpaidLeaveDays    days of APPROVED <em>unpaid</em> leave, deducted at the daily rate
 * @param absentDays         working days with neither attendance nor leave
 * @param lateMinutes        reported on the payslip, never deducted
 * @param overtimeMinutes    minutes worked after work-end on working days (already capped)
 * @param holidayMinutes     minutes worked on weekends and holidays (already capped)
 */
public record PayrollInput(
        BigDecimal baseSalary,
        int workingDaysInMonth,
        int payableWorkingDays,
        int unpaidLeaveDays,
        int absentDays,
        int lateMinutes,
        int overtimeMinutes,
        int holidayMinutes,
        PayrollRates rates) {

    public PayrollInput {
        if (baseSalary == null || baseSalary.signum() < 0) {
            throw new IllegalArgumentException("baseSalary must be present and >= 0");
        }
        if (rates == null) {
            throw new IllegalArgumentException("rates are required");
        }
        if (workingDaysInMonth < 0 || payableWorkingDays < 0) {
            throw new IllegalArgumentException("day counts must be >= 0");
        }
        if (payableWorkingDays > workingDaysInMonth) {
            throw new IllegalArgumentException("payableWorkingDays (" + payableWorkingDays
                    + ") cannot exceed workingDaysInMonth (" + workingDaysInMonth + ")");
        }
        if (unpaidLeaveDays < 0 || absentDays < 0 || lateMinutes < 0 || overtimeMinutes < 0
                || holidayMinutes < 0) {
            throw new IllegalArgumentException("day and minute counts must be >= 0");
        }
        baseSalary = Money.scaled(baseSalary);
    }

    /** A plain full month with no attendance adjustments at all. */
    public static PayrollInput plainMonth(BigDecimal baseSalary, int workingDaysInMonth,
                                          PayrollRates rates) {
        return new PayrollInput(baseSalary, workingDaysInMonth, workingDaysInMonth, 0, 0, 0, 0, 0, rates);
    }
}
