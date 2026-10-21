package com.example.hr.payroll;

import java.time.LocalDate;
import java.util.List;

/**
 * What is stored in the payslip's {@code snapshot} JSONB column: the period, the exact
 * configuration that produced the figures and the bracket-by-bracket tax breakdown.
 *
 * <p>This is the reason a payslip is auditable years later — the rates it was computed
 * with travel with it instead of being looked up again.
 */
public record PayslipSnapshot(
        int year,
        int month,
        LocalDate periodStart,
        LocalDate periodEnd,
        PayrollRates rates,
        List<TaxBracketCharge> taxBreakdown,
        String calculationNotes) {

    public static final String NOTES = """
            dailyRate = base / workingDaysInMonth; hourlyRate = dailyRate / workingHoursPerDay; \
            proratedBase = base for a full month, else round(base * payableWorkingDays / workingDaysInMonth); \
            overtimePay = overtimeHours * hourlyRate * overtimeMultiplier; \
            holidayPay = holidayHours * hourlyRate * holidayOvertimeMultiplier; \
            grossEarnings = proratedBase + overtimePay + holidayPay - unpaidLeaveDeduction - absenceDeduction; \
            insurance = clamp(base, minInsurable, maxInsurable) * employeeRate; \
            taxableIncome = max(0, grossEarnings - insurance - personalExemption); \
            tax = sum of marginal bracket charges, each rounded HALF_UP to 2 decimals; \
            netPay = max(0, grossEarnings - insurance - tax). Lateness is reported, never deducted. \
            Illustrative rates — not tax or legal advice.""";
}
