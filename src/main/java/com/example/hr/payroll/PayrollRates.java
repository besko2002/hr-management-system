package com.example.hr.payroll;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every configurable number the payroll maths uses, frozen into one immutable value.
 *
 * <p>It is stored verbatim in each payslip's JSON snapshot, which is what makes history
 * stable: changing {@code app.payroll.*} tomorrow cannot alter a payslip produced today.
 *
 * <p><b>The defaults are illustrative, simplified and not tax or legal advice.</b>
 */
public record PayrollRates(
        int workingHoursPerDay,
        BigDecimal overtimeMultiplier,
        BigDecimal holidayOvertimeMultiplier,
        BigDecimal insuranceEmployeeRate,
        BigDecimal minInsurableWage,
        BigDecimal maxInsurableWage,
        BigDecimal personalExemptionMonthly,
        List<TaxBracket> taxBrackets) {

    public PayrollRates {
        if (workingHoursPerDay < 1 || workingHoursPerDay > 24) {
            throw new IllegalArgumentException("app.payroll.working-hours-per-day must be between 1 and 24");
        }
        requireNonNegative(overtimeMultiplier, "overtime-multiplier");
        requireNonNegative(holidayOvertimeMultiplier, "holiday-overtime-multiplier");
        requireNonNegative(insuranceEmployeeRate, "insurance.employee-rate");
        requireNonNegative(minInsurableWage, "insurance.min-insurable");
        requireNonNegative(maxInsurableWage, "insurance.max-insurable");
        requireNonNegative(personalExemptionMonthly, "tax.personal-exemption-monthly");
        if (minInsurableWage.compareTo(maxInsurableWage) > 0) {
            throw new IllegalArgumentException("insurance.min-insurable must not exceed max-insurable");
        }
        if (taxBrackets == null || taxBrackets.isEmpty()) {
            throw new IllegalArgumentException("app.payroll.tax.brackets must not be empty");
        }
        List<TaxBracket> copy = List.copyOf(taxBrackets);
        BigDecimal previous = BigDecimal.ZERO;
        for (int i = 0; i < copy.size(); i++) {
            TaxBracket bracket = copy.get(i);
            if (bracket.isUnbounded() && i != copy.size() - 1) {
                throw new IllegalArgumentException("Only the last tax bracket may be open-ended");
            }
            if (!bracket.isUnbounded()) {
                if (bracket.upTo().compareTo(previous) <= 0) {
                    throw new IllegalArgumentException("Tax bracket ceilings must strictly increase, "
                            + bracket.upTo() + " follows " + previous);
                }
                previous = bracket.upTo();
            }
        }
        if (!copy.get(copy.size() - 1).isUnbounded()) {
            throw new IllegalArgumentException("The last tax bracket must be open-ended (no up-to)");
        }
        taxBrackets = copy;
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("app.payroll." + name + " must be >= 0, got " + value);
        }
    }
}
