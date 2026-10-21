package com.example.hr.payroll;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The payroll maths. Pure: a {@link PayrollInput} in, a {@link PayrollResult} out. No
 * database, no clock, no Spring, no {@code double} — every number is a
 * {@link BigDecimal} at scale 2, {@link java.math.RoundingMode#HALF_UP}.
 *
 * <h2>The rules, exactly</h2>
 * <ol>
 *   <li><b>Rates.</b> {@code dailyRate = base / workingDaysInMonth} and
 *       {@code hourlyRate = dailyRate / workingHoursPerDay}, each rounded to 2 decimals
 *       <em>before</em> being used, because both appear on the payslip and the employee
 *       must be able to reproduce every line from the printed numbers.</li>
 *   <li><b>Proration.</b> A full month pays exactly {@code base} — no
 *       {@code dailyRate × days} drift. A partial month (mid-month hire, mid-month
 *       termination) pays {@code round(base × payableWorkingDays / workingDaysInMonth)}.</li>
 *   <li><b>Deductions.</b> UNPAID leave days and ABSENT days are each charged at the
 *       rounded {@code dailyRate}. <b>Lateness costs nothing</b> — it is reported only.</li>
 *   <li><b>Additions.</b> {@code overtimeHours × hourlyRate × overtimeMultiplier} for
 *       weekday overtime, {@code holidayHours × hourlyRate × holidayOvertimeMultiplier}
 *       for hours worked on a weekend or a public holiday. Hours come from the attendance
 *       module already capped per day.</li>
 *   <li><b>Insurance.</b> {@code clamp(base, minInsurable, maxInsurable) × employeeRate}.
 *       It is charged on the contractual monthly base — not on overtime, and not pro-rated
 *       — which is the simplification this project makes explicit. A base of 0 pays 0.</li>
 *   <li><b>Tax.</b> {@code taxable = max(0, grossEarnings − insurance − exemption)}, then
 *       the brackets applied <em>marginally</em>. Each bracket's charge is rounded on its
 *       own and the rounded charges are summed, so the breakdown printed on the payslip
 *       adds up to the tax line exactly.</li>
 *   <li><b>Floor.</b> {@code net = grossEarnings − insurance − tax}; if that is negative
 *       the net is 0.00 and {@link PayrollResult#netFloored()} is set, so the payslip can
 *       warn instead of showing a debt.</li>
 * </ol>
 *
 * <p><b>The default rates are illustrative and simplified — not tax or legal advice.</b>
 */
public final class PayrollCalculator {

    public PayrollResult calculate(PayrollInput input) {
        PayrollRates rates = input.rates();
        BigDecimal base = input.baseSalary();

        BigDecimal dailyRate = Money.divide(base, input.workingDaysInMonth());
        BigDecimal hourlyRate = Money.divide(dailyRate, rates.workingHoursPerDay());

        BigDecimal proratedBase = prorate(base, input.payableWorkingDays(), input.workingDaysInMonth());

        BigDecimal overtimeHours = Money.hoursOf(input.overtimeMinutes());
        BigDecimal holidayHours = Money.hoursOf(input.holidayMinutes());
        BigDecimal overtimePay = Money.scaled(
                overtimeHours.multiply(hourlyRate).multiply(rates.overtimeMultiplier()));
        BigDecimal holidayPay = Money.scaled(
                holidayHours.multiply(hourlyRate).multiply(rates.holidayOvertimeMultiplier()));

        BigDecimal unpaidLeaveDeduction = Money.multiply(dailyRate,
                BigDecimal.valueOf(input.unpaidLeaveDays()));
        BigDecimal absenceDeduction = Money.multiply(dailyRate, BigDecimal.valueOf(input.absentDays()));

        BigDecimal grossEarnings = Money.scaled(proratedBase
                .add(overtimePay)
                .add(holidayPay)
                .subtract(unpaidLeaveDeduction)
                .subtract(absenceDeduction));

        BigDecimal insurableWage = base.signum() == 0
                ? Money.ZERO
                : Money.clamp(base, rates.minInsurableWage(), rates.maxInsurableWage());
        BigDecimal insurance = Money.multiply(insurableWage, rates.insuranceEmployeeRate());

        BigDecimal exemption = Money.scaled(rates.personalExemptionMonthly());
        BigDecimal taxableIncome = Money.atLeastZero(
                grossEarnings.subtract(insurance).subtract(exemption));

        List<TaxBracketCharge> breakdown = taxBreakdown(taxableIncome, rates.taxBrackets());
        BigDecimal tax = breakdown.stream()
                .map(TaxBracketCharge::tax)
                .reduce(Money.ZERO, BigDecimal::add);
        tax = Money.scaled(tax);

        BigDecimal net = Money.scaled(grossEarnings.subtract(insurance).subtract(tax));
        boolean floored = net.signum() < 0;

        return new PayrollResult(base, input.workingDaysInMonth(), input.payableWorkingDays(),
                input.unpaidLeaveDays(), input.absentDays(), input.lateMinutes(), input.overtimeMinutes(),
                input.holidayMinutes(), dailyRate, hourlyRate, overtimeHours, holidayHours, proratedBase,
                overtimePay, holidayPay, unpaidLeaveDeduction, absenceDeduction, grossEarnings,
                insurableWage, insurance, exemption, taxableIncome, tax, breakdown,
                floored ? Money.ZERO : net, floored, rates);
    }

    /** A full month is paid exactly; anything shorter is scaled by the payable working days. */
    private static BigDecimal prorate(BigDecimal base, int payableDays, int workingDays) {
        if (workingDays <= 0 || payableDays <= 0) {
            return Money.ZERO;
        }
        if (payableDays >= workingDays) {
            return Money.scaled(base);
        }
        return Money.divide(base.multiply(BigDecimal.valueOf(payableDays)),
                BigDecimal.valueOf(workingDays));
    }

    /**
     * The marginal application of the brackets. Income exactly on a ceiling is fully taxed
     * by that bracket and nothing spills into the next one.
     */
    static List<TaxBracketCharge> taxBreakdown(BigDecimal taxable, List<TaxBracket> brackets) {
        List<TaxBracketCharge> charges = new ArrayList<>();
        if (taxable.signum() <= 0) {
            return charges;
        }
        BigDecimal lower = BigDecimal.ZERO;
        for (TaxBracket bracket : brackets) {
            if (taxable.compareTo(lower) <= 0) {
                break;
            }
            BigDecimal ceiling = bracket.isUnbounded() ? taxable : Money.min(taxable, bracket.upTo());
            BigDecimal slice = Money.scaled(ceiling.subtract(lower));
            if (slice.signum() > 0) {
                charges.add(new TaxBracketCharge(Money.scaled(lower),
                        bracket.isUnbounded() ? null : Money.scaled(bracket.upTo()),
                        bracket.rate(), slice, Money.multiply(slice, bracket.rate())));
            }
            lower = bracket.isUnbounded() ? taxable : bracket.upTo();
        }
        return charges;
    }
}
