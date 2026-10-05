package com.example.hr.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Pure unit tests of the payroll maths against <b>hand-computed</b> figures. Every expected
 * number in this file was worked out on paper from the documented formulas, which is the
 * whole point: if the implementation drifts, these fail.
 *
 * <p>Rates used throughout (the shipped illustrative defaults): 8 h/day, overtime ×1.5,
 * holiday ×2.0, insurance 11 % of the base clamped to [2000, 12600], monthly exemption
 * 1250, brackets 0–1500 @0 %, –3000 @10 %, –5000 @15 %, –8000 @20 %, –12000 @22.5 %,
 * above @25 %.
 */
class PayrollCalculatorTest {

    private static final PayrollCalculator CALCULATOR = new PayrollCalculator();
    private static final PayrollRates RATES = PayrollRatesFixture.defaults();

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------ a plain month

    /**
     * base 10 000, 22 working days, nothing unusual.
     * daily 10000/22 = 454.5454… → 454.55; hourly 454.55/8 = 56.81875 → 56.82;
     * gross 10 000; insurance 10 000×0.11 = 1 100; taxable 10 000−1 100−1 250 = 7 650;
     * tax 0 + 150 + 300 + 2 650×0.20 = 530 → 980; net 10 000−1 100−980 = 7 920.
     */
    @Test
    void aPlainMonthIsPaidInFullWithInsuranceAndProgressiveTax() {
        PayrollResult result = CALCULATOR.calculate(
                PayrollInput.plainMonth(money("10000.00"), 22, RATES));

        assertThat(result.dailyRate()).isEqualByComparingTo("454.55");
        assertThat(result.hourlyRate()).isEqualByComparingTo("56.82");
        assertThat(result.proratedBase()).isEqualByComparingTo("10000.00");
        assertThat(result.grossEarnings()).isEqualByComparingTo("10000.00");
        assertThat(result.insurableWage()).isEqualByComparingTo("10000.00");
        assertThat(result.insurance()).isEqualByComparingTo("1100.00");
        assertThat(result.taxableIncome()).isEqualByComparingTo("7650.00");
        assertThat(result.tax()).isEqualByComparingTo("980.00");
        assertThat(result.netPay()).isEqualByComparingTo("7920.00");
        assertThat(result.netFloored()).isFalse();
    }

    @Test
    void aFullMonthPaysExactlyTheBaseAndNeverTheRoundedDailyRateTimesDays() {
        // 454.55 × 22 = 10 000.10 — the proration must not introduce that 10-piastre drift.
        PayrollResult result = CALCULATOR.calculate(
                PayrollInput.plainMonth(money("10000.00"), 22, RATES));

        assertThat(result.proratedBase()).isEqualByComparingTo("10000.00");
        assertThat(result.dailyRate().multiply(BigDecimal.valueOf(22)))
                .isEqualByComparingTo("10000.10");
    }

    @Test
    void everyMoneyFieldIsScaledToTwoDecimals() {
        PayrollResult result = CALCULATOR.calculate(
                PayrollInput.plainMonth(money("7000"), 21, RATES));

        assertThat(List.of(result.baseSalary(), result.dailyRate(), result.hourlyRate(),
                        result.proratedBase(), result.overtimePay(), result.holidayPay(),
                        result.unpaidLeaveDeduction(), result.absenceDeduction(), result.grossEarnings(),
                        result.insurableWage(), result.insurance(), result.personalExemption(),
                        result.taxableIncome(), result.tax(), result.netPay()))
                .allSatisfy(value -> assertThat(value.scale()).isEqualTo(2));
    }

    // ------------------------------------------------------------------ proration

    /**
     * Mid-month hire: 22 working days in the month, only 10 of them inside the employment
     * window. prorated 10 000×10/22 = 4 545.4545… → 4 545.45; insurance still 1 100
     * (charged on the contractual base); taxable 4 545.45−1 100−1 250 = 2 195.45;
     * tax 695.45×0.10 = 69.545 → 69.55; net 4 545.45−1 100−69.55 = 3 375.90.
     */
    @Test
    void aMidMonthHireIsProratedByPayableWorkingDays() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("10000.00"), 22, 10, 0, 0, 0, 0, 0, RATES));

        assertThat(result.proratedBase()).isEqualByComparingTo("4545.45");
        assertThat(result.grossEarnings()).isEqualByComparingTo("4545.45");
        assertThat(result.insurance()).isEqualByComparingTo("1100.00");
        assertThat(result.taxableIncome()).isEqualByComparingTo("2195.45");
        assertThat(result.tax()).isEqualByComparingTo("69.55");
        assertThat(result.netPay()).isEqualByComparingTo("3375.90");
    }

    /**
     * Mid-month termination: base 6 000, 21 working days, 12 payable up to the leaving date.
     * prorated 6 000×12/21 = 3 428.5714… → 3 428.57; insurance 660;
     * taxable 3 428.57−660−1 250 = 1 518.57; tax 18.57×0.10 = 1.857 → 1.86;
     * net 3 428.57−660−1.86 = 2 766.71.
     */
    @Test
    void aMidMonthTerminationIsProratedToTheLeavingDate() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("6000.00"), 21, 12, 0, 0, 0, 0, 0, RATES));

        assertThat(result.dailyRate()).isEqualByComparingTo("285.71");
        assertThat(result.proratedBase()).isEqualByComparingTo("3428.57");
        assertThat(result.insurance()).isEqualByComparingTo("660.00");
        assertThat(result.taxableIncome()).isEqualByComparingTo("1518.57");
        assertThat(result.tax()).isEqualByComparingTo("1.86");
        assertThat(result.netPay()).isEqualByComparingTo("2766.71");
    }

    @Test
    void zeroPayableDaysEarnNothingButStillPayInsuranceAndFloorTheNet() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("5000.00"), 22, 0, 0, 0, 0, 0, 0, RATES));

        assertThat(result.proratedBase()).isEqualByComparingTo("0.00");
        assertThat(result.grossEarnings()).isEqualByComparingTo("0.00");
        assertThat(result.insurance()).isEqualByComparingTo("550.00");
        assertThat(result.taxableIncome()).isEqualByComparingTo("0.00");
        assertThat(result.netPay()).isEqualByComparingTo("0.00");
        assertThat(result.netFloored()).isTrue();
    }

    @Test
    void aMonthWithoutAnyWorkingDayCannotDivideByZero() {
        PayrollResult result = CALCULATOR.calculate(
                PayrollInput.plainMonth(money("5000.00"), 0, RATES));

        assertThat(result.dailyRate()).isEqualByComparingTo("0.00");
        assertThat(result.hourlyRate()).isEqualByComparingTo("0.00");
        assertThat(result.proratedBase()).isEqualByComparingTo("0.00");
        assertThat(result.netPay()).isEqualByComparingTo("0.00");
        assertThat(result.netFloored()).isTrue();
    }

    // ------------------------------------------------------------------ deductions

    /**
     * base 8 800, 22 working days → daily exactly 400.00. Three unpaid-leave days cost
     * 1 200; gross 7 600; insurance 968; taxable 7 600−968−1 250 = 5 382;
     * tax 0 + 150 + 300 + 382×0.20 = 76.40 → 526.40; net 7 600−968−526.40 = 6 105.60.
     */
    @Test
    void unpaidLeaveDaysAreDeductedAtTheDailyRate() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("8800.00"), 22, 22, 3, 0, 0, 0, 0, RATES));

        assertThat(result.dailyRate()).isEqualByComparingTo("400.00");
        assertThat(result.unpaidLeaveDeduction()).isEqualByComparingTo("1200.00");
        assertThat(result.grossEarnings()).isEqualByComparingTo("7600.00");
        assertThat(result.taxableIncome()).isEqualByComparingTo("5382.00");
        assertThat(result.tax()).isEqualByComparingTo("526.40");
        assertThat(result.netPay()).isEqualByComparingTo("6105.60");
    }

    /**
     * base 4 400, 22 days → daily 200. Two absences cost 400; gross 4 000; insurance 484;
     * taxable 4 000−484−1 250 = 2 266; tax 766×0.10 = 76.60;
     * net 4 000−484−76.60 = 3 439.40.
     */
    @Test
    void absentDaysAreDeductedAtTheDailyRate() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("4400.00"), 22, 22, 0, 2, 0, 0, 0, RATES));

        assertThat(result.absenceDeduction()).isEqualByComparingTo("400.00");
        assertThat(result.grossEarnings()).isEqualByComparingTo("4000.00");
        assertThat(result.tax()).isEqualByComparingTo("76.60");
        assertThat(result.netPay()).isEqualByComparingTo("3439.40");
    }

    @Test
    void latenessIsReportedButNeverDeducted() {
        PayrollResult withLateness = CALCULATOR.calculate(new PayrollInput(
                money("8800.00"), 22, 22, 0, 0, 600, 0, 0, RATES));
        PayrollResult without = CALCULATOR.calculate(
                PayrollInput.plainMonth(money("8800.00"), 22, RATES));

        assertThat(withLateness.lateMinutes()).isEqualTo(600);
        assertThat(withLateness.netPay()).isEqualByComparingTo(without.netPay());
    }

    @Test
    void unpaidLeaveAndAbsenceAreBothSummedIntoTheAttendanceDeductions() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("8800.00"), 22, 22, 2, 1, 0, 0, 0, RATES));

        assertThat(result.unpaidLeaveDeduction()).isEqualByComparingTo("800.00");
        assertThat(result.absenceDeduction()).isEqualByComparingTo("400.00");
        assertThat(result.totalAttendanceDeductions()).isEqualByComparingTo("1200.00");
        assertThat(result.grossEarnings()).isEqualByComparingTo("7600.00");
    }

    // ------------------------------------------------------------------ overtime

    /**
     * base 8 800, 22 days → daily 400, hourly 50. 300 overtime minutes = 5 h ×50×1.5 = 375;
     * 240 holiday minutes = 4 h ×50×2.0 = 400. gross 8 800+375+400 = 9 575; insurance 968;
     * taxable 9 575−968−1 250 = 7 357; tax 0+150+300+2 357×0.20 = 471.40 → 921.40;
     * net 9 575−968−921.40 = 7 685.60.
     */
    @Test
    void overtimeAndHolidayWorkArePaidAtTheirOwnMultipliers() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("8800.00"), 22, 22, 0, 0, 0, 300, 240, RATES));

        assertThat(result.hourlyRate()).isEqualByComparingTo("50.00");
        assertThat(result.overtimeHours()).isEqualByComparingTo("5.00");
        assertThat(result.holidayHours()).isEqualByComparingTo("4.00");
        assertThat(result.overtimePay()).isEqualByComparingTo("375.00");
        assertThat(result.holidayPay()).isEqualByComparingTo("400.00");
        assertThat(result.grossEarnings()).isEqualByComparingTo("9575.00");
        assertThat(result.tax()).isEqualByComparingTo("921.40");
        assertThat(result.netPay()).isEqualByComparingTo("7685.60");
    }

    @Test
    void overtimeMinutesBecomeHoursRoundedHalfUpToTwoDecimals() {
        // 100 minutes = 1.666… h → 1.67 h; 1.67 × 50 × 1.5 = 125.25.
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("8800.00"), 22, 22, 0, 0, 0, 100, 0, RATES));

        assertThat(result.overtimeHours()).isEqualByComparingTo("1.67");
        assertThat(result.overtimePay()).isEqualByComparingTo("125.25");
    }

    @Test
    void holidayWorkAloneIsPaidAtDoubleTheHourlyRate() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("8800.00"), 22, 22, 0, 0, 0, 0, 90, RATES));

        assertThat(result.holidayHours()).isEqualByComparingTo("1.50");
        assertThat(result.holidayPay()).isEqualByComparingTo("150.00");
        assertThat(result.overtimePay()).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------ insurance clamping

    /** base 1 500 is below the floor, so insurance is charged on 2 000: 220. */
    @Test
    void insuranceIsClampedUpToTheMinimumInsurableWage() {
        PayrollResult result = CALCULATOR.calculate(
                PayrollInput.plainMonth(money("1500.00"), 20, RATES));

        assertThat(result.insurableWage()).isEqualByComparingTo("2000.00");
        assertThat(result.insurance()).isEqualByComparingTo("220.00");
        assertThat(result.taxableIncome()).isEqualByComparingTo("30.00");
        assertThat(result.tax()).isEqualByComparingTo("0.00");
        assertThat(result.netPay()).isEqualByComparingTo("1280.00");
    }

    /**
     * base 30 000 is above the ceiling, so insurance is charged on 12 600: 1 386.
     * taxable 30 000−1 386−1 250 = 27 364;
     * tax 0+150+300+600+900+15 364×0.25 = 3 841 → 5 791; net 30 000−1 386−5 791 = 22 823.
     */
    @Test
    void insuranceIsClampedDownToTheMaximumInsurableWage() {
        PayrollResult result = CALCULATOR.calculate(
                PayrollInput.plainMonth(money("30000.00"), 20, RATES));

        assertThat(result.insurableWage()).isEqualByComparingTo("12600.00");
        assertThat(result.insurance()).isEqualByComparingTo("1386.00");
        assertThat(result.taxableIncome()).isEqualByComparingTo("27364.00");
        assertThat(result.tax()).isEqualByComparingTo("5791.00");
        assertThat(result.netPay()).isEqualByComparingTo("22823.00");
    }

    @Test
    void exactlyOnTheInsuranceBandBoundariesNothingIsClamped() {
        assertThat(CALCULATOR.calculate(PayrollInput.plainMonth(money("2000.00"), 20, RATES))
                .insurableWage()).isEqualByComparingTo("2000.00");
        assertThat(CALCULATOR.calculate(PayrollInput.plainMonth(money("12600.00"), 20, RATES))
                .insurableWage()).isEqualByComparingTo("12600.00");
        assertThat(CALCULATOR.calculate(PayrollInput.plainMonth(money("12600.00"), 20, RATES))
                .insurance()).isEqualByComparingTo("1386.00");
    }

    @Test
    void aZeroBaseSalaryPaysNoInsuranceAtAll() {
        PayrollResult result = CALCULATOR.calculate(PayrollInput.plainMonth(money("0.00"), 22, RATES));

        assertThat(result.insurableWage()).isEqualByComparingTo("0.00");
        assertThat(result.insurance()).isEqualByComparingTo("0.00");
        assertThat(result.netPay()).isEqualByComparingTo("0.00");
        assertThat(result.netFloored()).isFalse();
    }

    // ------------------------------------------------------------------ tax brackets

    @Test
    void taxIsZeroUpToAndIncludingTheFirstBracketCeiling() {
        assertThat(taxOn("1500.00")).isEqualByComparingTo("0.00");
    }

    @Test
    void eachBracketBoundaryMatchesTheHandComputedCumulativeTax() {
        assertThat(taxOn("3000.00")).isEqualByComparingTo("150.00");    // 1500×0.10
        assertThat(taxOn("5000.00")).isEqualByComparingTo("450.00");    // +2000×0.15
        assertThat(taxOn("8000.00")).isEqualByComparingTo("1050.00");   // +3000×0.20
        assertThat(taxOn("12000.00")).isEqualByComparingTo("1950.00");  // +4000×0.225
        assertThat(taxOn("15000.00")).isEqualByComparingTo("2700.00");  // +3000×0.25
    }

    @Test
    void oneUnitAboveABoundaryOnlyTaxesThatUnitAtTheHigherRate() {
        assertThat(taxOn("1501.00")).isEqualByComparingTo("0.10");
        assertThat(taxOn("3001.00")).isEqualByComparingTo("150.15");
        assertThat(taxOn("5001.00")).isEqualByComparingTo("450.20");
        assertThat(taxOn("8001.00")).isEqualByComparingTo("1050.23"); // 0.225 → 0.225 → 0.23
        assertThat(taxOn("12001.00")).isEqualByComparingTo("1950.25");
    }

    @Test
    void theBracketBreakdownIsMarginalAndAddsUpToTheTaxLine() {
        List<TaxBracketCharge> breakdown =
                PayrollCalculator.taxBreakdown(money("7650.00"), RATES.taxBrackets());

        assertThat(breakdown).hasSize(4);
        assertThat(breakdown.get(0).taxedAmount()).isEqualByComparingTo("1500.00");
        assertThat(breakdown.get(0).tax()).isEqualByComparingTo("0.00");
        assertThat(breakdown.get(1).taxedAmount()).isEqualByComparingTo("1500.00");
        assertThat(breakdown.get(1).tax()).isEqualByComparingTo("150.00");
        assertThat(breakdown.get(2).taxedAmount()).isEqualByComparingTo("2000.00");
        assertThat(breakdown.get(2).tax()).isEqualByComparingTo("300.00");
        assertThat(breakdown.get(3).taxedAmount()).isEqualByComparingTo("2650.00");
        assertThat(breakdown.get(3).tax()).isEqualByComparingTo("530.00");
        assertThat(breakdown.stream().map(TaxBracketCharge::tax)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("980.00");
    }

    @Test
    void theTopBracketIsOpenEndedAndReportedWithoutACeiling() {
        List<TaxBracketCharge> breakdown =
                PayrollCalculator.taxBreakdown(money("20000.00"), RATES.taxBrackets());

        assertThat(breakdown).hasSize(6);
        assertThat(breakdown.get(5).to()).isNull();
        assertThat(breakdown.get(5).taxedAmount()).isEqualByComparingTo("8000.00");
        assertThat(breakdown.get(5).tax()).isEqualByComparingTo("2000.00");
    }

    @Test
    void zeroTaxableIncomeProducesNoBracketChargesAtAll() {
        assertThat(PayrollCalculator.taxBreakdown(money("0.00"), RATES.taxBrackets())).isEmpty();
        assertThat(PayrollCalculator.taxBreakdown(money("-10.00"), RATES.taxBrackets())).isEmpty();
    }

    // ------------------------------------------------------------------ floors

    /**
     * base 1 400: insurance is clamped up to 2 000 → 220, and 1 400−220 = 1 180 is below
     * the 1 250 exemption, so the taxable income floors at 0 instead of going negative.
     */
    @Test
    void theTaxableIncomeFloorsAtZeroWhenTheExemptionSwallowsTheWholeSalary() {
        PayrollResult result = CALCULATOR.calculate(
                PayrollInput.plainMonth(money("1400.00"), 20, RATES));

        assertThat(result.insurance()).isEqualByComparingTo("220.00");
        assertThat(result.taxableIncome()).isEqualByComparingTo("0.00");
        assertThat(result.tax()).isEqualByComparingTo("0.00");
        assertThat(result.netPay()).isEqualByComparingTo("1180.00");
        assertThat(result.netFloored()).isFalse();
    }

    /**
     * base 4 400, 22 days → daily 200, and 22 absent days wipe out the whole base.
     * gross 0; insurance 484; net 0−484 = −484 → floored to 0 and flagged.
     */
    @Test
    void theNetFloorsAtZeroAndIsFlaggedWhenDeductionsExceedEarnings() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("4400.00"), 22, 22, 0, 22, 0, 0, 0, RATES));

        assertThat(result.absenceDeduction()).isEqualByComparingTo("4400.00");
        assertThat(result.grossEarnings()).isEqualByComparingTo("0.00");
        assertThat(result.insurance()).isEqualByComparingTo("484.00");
        assertThat(result.netPay()).isEqualByComparingTo("0.00");
        assertThat(result.netFloored()).isTrue();
    }

    @Test
    void deductionsBeyondTheEarningsStillLeaveANegativeGrossVisibleForAuditing() {
        PayrollResult result = CALCULATOR.calculate(new PayrollInput(
                money("4400.00"), 22, 22, 10, 15, 0, 0, 0, RATES));

        // 25 charged days × 200 = 5 000 against a 4 400 base.
        assertThat(result.grossEarnings()).isEqualByComparingTo("-600.00");
        assertThat(result.netPay()).isEqualByComparingTo("0.00");
        assertThat(result.netFloored()).isTrue();
    }

    // ------------------------------------------------------------------ rounding

    @Test
    void theDailyRateRoundsHalfUpOnExactlyFiveThousandths() {
        // 100.04 / 8 = 12.505 → 12.51 (HALF_UP), not 12.50 (HALF_EVEN/HALF_DOWN).
        PayrollResult result = CALCULATOR.calculate(PayrollInput.plainMonth(money("100.04"), 8, RATES));

        assertThat(result.dailyRate()).isEqualByComparingTo("12.51");
        assertThat(result.hourlyRate()).isEqualByComparingTo("1.56");
    }

    /**
     * base 5 000.05, 20 days. insurance 5 000.05×0.11 = 550.0055 → 550.01 (HALF_UP);
     * taxable 5 000.05−550.01−1 250 = 3 200.04;
     * tax 0 + 150 + 200.04×0.15 = 30.006 → 30.01 → 180.01;
     * net 5 000.05−550.01−180.01 = 4 270.03.
     */
    @Test
    void insuranceAndTaxBothRoundHalfUpOnExactlyFiveThousandths() {
        PayrollResult result = CALCULATOR.calculate(
                PayrollInput.plainMonth(money("5000.05"), 20, RATES));

        assertThat(result.insurance()).isEqualByComparingTo("550.01");
        assertThat(result.taxableIncome()).isEqualByComparingTo("3200.04");
        assertThat(result.tax()).isEqualByComparingTo("180.01");
        assertThat(result.netPay()).isEqualByComparingTo("4270.03");
    }

    @Test
    void aBracketChargeOfExactlyHalfAPiastreRoundsUp() {
        // The top slice is 0.10 at 25 % = 0.025 → 0.03.
        List<TaxBracketCharge> breakdown =
                PayrollCalculator.taxBreakdown(money("12000.10"), RATES.taxBrackets());

        assertThat(breakdown.get(5).taxedAmount()).isEqualByComparingTo("0.10");
        assertThat(breakdown.get(5).tax()).isEqualByComparingTo("0.03");
        assertThat(breakdown.stream().map(TaxBracketCharge::tax)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1950.03");
    }

    @Test
    void moneyHelpersRoundHalfUpAndNeverGoNegativeWhereAFloorApplies() {
        assertThat(Money.scaled(money("0.005"))).isEqualByComparingTo("0.01");
        assertThat(Money.scaled(money("0.004"))).isEqualByComparingTo("0.00");
        assertThat(Money.atLeastZero(money("-0.01"))).isEqualByComparingTo("0.00");
        assertThat(Money.divide(money("10.00"), 0)).isEqualByComparingTo("0.00");
        assertThat(Money.hoursOf(90)).isEqualByComparingTo("1.50");
        assertThat(Money.hoursOf(100)).isEqualByComparingTo("1.67");
        assertThat(Money.clamp(money("5.00"), money("1.00"), money("3.00")))
                .isEqualByComparingTo("3.00");
    }

    // ------------------------------------------------------------------ guards

    @Test
    void payableDaysCannotExceedTheWorkingDaysOfTheMonth() {
        assertThat(catchThrowable(() -> new PayrollInput(
                money("1000.00"), 20, 21, 0, 0, 0, 0, 0, RATES)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payableWorkingDays");
    }

    @Test
    void aNegativeBaseSalaryIsRejected() {
        assertThat(catchThrowable(() -> PayrollInput.plainMonth(money("-1.00"), 20, RATES)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theBracketTableMustEndWithAnOpenEndedBracket() {
        assertThat(catchThrowable(() -> new PayrollRates(8, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO,
                        List.of(new TaxBracket(money("100"), money("0.1"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("open-ended");
    }

    @Test
    void bracketCeilingsMustStrictlyIncrease() {
        assertThat(catchThrowable(() -> new PayrollRates(8, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO,
                        List.of(new TaxBracket(money("200"), money("0.1")),
                                new TaxBracket(money("100"), money("0.2")),
                                new TaxBracket(null, money("0.3"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly increase");
    }

    @Test
    void aTaxRateOutsideZeroToOneIsRejected() {
        assertThat(catchThrowable(() -> new TaxBracket(money("100"), money("1.5"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> new TaxBracket(money("100"), money("-0.1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theInsurableBandMustNotBeInverted() {
        assertThat(catchThrowable(() -> new PayrollRates(8, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, money("100"), money("10"), BigDecimal.ZERO,
                        List.of(new TaxBracket(null, money("0.1"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("min-insurable");
    }

    private static BigDecimal taxOn(String taxable) {
        return PayrollCalculator.taxBreakdown(money(taxable), RATES.taxBrackets()).stream()
                .map(TaxBracketCharge::tax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
