package com.example.hr.leave;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure tests of the balance arithmetic: the computed {@code remaining}, the refund
 * accounting of every transition, the new-joiner pro-rating formula and the whole-day
 * monthly accrual increments.
 */
class LeaveBalanceMathTest {

    private static final int ANNUAL_ALLOWANCE = 21;
    private static final int SICK_ALLOWANCE = 10;

    private static LeaveBalance balance(int entitled, int carriedOver) {
        return new LeaveBalance(UUID.randomUUID(), LeaveType.ANNUAL, 2025, entitled, carriedOver);
    }

    // ------------------------------------------------------------------ remaining

    @Test
    void aFreshBalanceHasNothingUsedOrPending() {
        LeaveBalance balance = balance(21, 0);
        assertThat(balance.getUsedDays()).isZero();
        assertThat(balance.getPendingDays()).isZero();
        assertThat(balance.remainingDays()).isEqualTo(21);
    }

    @Test
    void remainingIsEntitledPlusCarriedMinusUsedMinusPending() {
        LeaveBalance balance = balance(21, 4);
        balance.reserve(3);
        balance.consumeReserved(3);
        balance.reserve(2);
        assertThat(balance.getEntitledDays()).isEqualTo(21);
        assertThat(balance.getCarriedOverDays()).isEqualTo(4);
        assertThat(balance.getUsedDays()).isEqualTo(3);
        assertThat(balance.getPendingDays()).isEqualTo(2);
        assertThat(balance.remainingDays()).isEqualTo(21 + 4 - 3 - 2);
    }

    @Test
    void reservingDaysLowersTheRemainingImmediately() {
        LeaveBalance balance = balance(21, 0);
        balance.reserve(5);
        assertThat(balance.remainingDays()).isEqualTo(16);
        assertThat(balance.getUsedDays()).isZero();
    }

    // ------------------------------------------------------------------ refund accounting

    @Test
    void rejectingAPendingRequestReturnsTheExactNumbers() {
        LeaveBalance balance = balance(21, 2);
        balance.reserve(5);
        balance.releaseReserved(5);
        assertThat(balance.getPendingDays()).isZero();
        assertThat(balance.getUsedDays()).isZero();
        assertThat(balance.remainingDays()).isEqualTo(23);
    }

    @Test
    void approvingMovesDaysFromPendingToUsedWithoutChangingRemaining() {
        LeaveBalance balance = balance(21, 0);
        balance.reserve(5);
        int remainingWhilePending = balance.remainingDays();
        balance.consumeReserved(5);
        assertThat(balance.getPendingDays()).isZero();
        assertThat(balance.getUsedDays()).isEqualTo(5);
        assertThat(balance.remainingDays()).isEqualTo(remainingWhilePending).isEqualTo(16);
    }

    @Test
    void cancellingAnApprovedRequestReturnsTheExactNumbers() {
        LeaveBalance balance = balance(21, 0);
        balance.reserve(5);
        balance.consumeReserved(5);
        balance.releaseUsed(5);
        assertThat(balance.getUsedDays()).isZero();
        assertThat(balance.getPendingDays()).isZero();
        assertThat(balance.remainingDays()).isEqualTo(21);
    }

    @Test
    void aLongSequenceOfTransitionsAlwaysBalancesOut() {
        LeaveBalance balance = balance(21, 3);
        int before = balance.remainingDays();
        balance.reserve(4);
        balance.consumeReserved(4);      // approved
        balance.releaseUsed(4);          // cancelled before it started
        balance.reserve(2);
        balance.releaseReserved(2);      // rejected
        balance.reserve(6);
        balance.releaseReserved(6);      // cancelled while pending
        assertThat(balance.remainingDays()).isEqualTo(before);
        assertThat(balance.getUsedDays()).isZero();
        assertThat(balance.getPendingDays()).isZero();
    }

    @Test
    void cannotReleaseMoreThanIsReserved() {
        LeaveBalance balance = balance(21, 0);
        balance.reserve(3);
        assertThatThrownBy(() -> balance.releaseReserved(4)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotRefundMoreThanIsUsed() {
        LeaveBalance balance = balance(21, 0);
        balance.reserve(3);
        balance.consumeReserved(3);
        assertThatThrownBy(() -> balance.releaseUsed(4)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void zeroOrNegativeDayMovementsAreRejected() {
        LeaveBalance balance = balance(21, 0);
        assertThatThrownBy(() -> balance.reserve(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.reserve(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.credit(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creditingRaisesTheEntitlementAndTheRemaining() {
        LeaveBalance balance = balance(0, 0);
        balance.credit(2);
        balance.credit(2);
        assertThat(balance.getEntitledDays()).isEqualTo(4);
        assertThat(balance.remainingDays()).isEqualTo(4);
    }

    // ------------------------------------------------------------------ pro-rating

    /**
     * round(21 × (13 − hireMonth) / 12) — the hire month counts as a whole month, so a
     * January joiner gets the full 21 days and a December joiner gets 2.
     */
    @ParameterizedTest(name = "hired in month {0} -> {1} ANNUAL days")
    @CsvSource({"1,21", "2,19", "3,18", "4,16", "5,14", "6,12", "7,11", "8,9", "9,7", "10,5", "11,4", "12,2"})
    void annualEntitlementIsProRatedByHireMonth(int hireMonth, int expectedDays) {
        LocalDate hireDate = LocalDate.of(2025, hireMonth, 15);
        assertThat(LeaveBalanceService.initialEntitlement(ANNUAL_ALLOWANCE, hireDate, 2025))
                .isEqualTo(expectedDays);
    }

    @ParameterizedTest(name = "hired in month {0} -> {1} SICK days")
    @CsvSource({"1,10", "4,8", "7,5", "10,3", "12,1"})
    void sickEntitlementUsesTheSameFormula(int hireMonth, int expectedDays) {
        LocalDate hireDate = LocalDate.of(2025, hireMonth, 1);
        assertThat(LeaveBalanceService.initialEntitlement(SICK_ALLOWANCE, hireDate, 2025)).isEqualTo(expectedDays);
    }

    @Test
    void theDayOfTheHireMonthDoesNotMatter() {
        assertThat(LeaveBalanceService.initialEntitlement(ANNUAL_ALLOWANCE, LocalDate.of(2025, 7, 1), 2025))
                .isEqualTo(LeaveBalanceService.initialEntitlement(ANNUAL_ALLOWANCE,
                        LocalDate.of(2025, 7, 31), 2025))
                .isEqualTo(11);
    }

    @Test
    void anyYearAfterTheHireYearGivesTheFullAllowance() {
        LocalDate hireDate = LocalDate.of(2024, 11, 20);
        assertThat(LeaveBalanceService.initialEntitlement(ANNUAL_ALLOWANCE, hireDate, 2025)).isEqualTo(21);
        assertThat(LeaveBalanceService.initialEntitlement(ANNUAL_ALLOWANCE, hireDate, 2030)).isEqualTo(21);
    }

    @Test
    void aYearBeforeTheHireYearGivesNothing() {
        assertThat(LeaveBalanceService.initialEntitlement(ANNUAL_ALLOWANCE, LocalDate.of(2025, 1, 1), 2024))
                .isZero();
    }

    @Test
    void aTypeWithoutAnAllowanceGivesNothing() {
        assertThat(LeaveBalanceService.initialEntitlement(0, LocalDate.of(2020, 1, 1), 2025)).isZero();
    }

    // ------------------------------------------------------------------ monthly accrual increments

    @ParameterizedTest(name = "month {0} credits {1} ANNUAL day(s)")
    @CsvSource({"1,2", "2,2", "3,1", "4,2", "5,2", "6,2", "7,1", "8,2", "9,2", "10,2", "11,1", "12,2"})
    void theMonthlyCreditIsAWholeNumberOfDays(int month, int expectedDays) {
        assertThat(LeaveAccrualService.monthlyCredit(ANNUAL_ALLOWANCE, month)).isEqualTo(expectedDays);
    }

    @Test
    void twelveMonthlyCreditsAddUpToExactlyTheYearlyAllowance() {
        int total = 0;
        for (int month = 1; month <= 12; month++) {
            total += LeaveAccrualService.monthlyCredit(ANNUAL_ALLOWANCE, month);
        }
        assertThat(total).isEqualTo(ANNUAL_ALLOWANCE);
    }

    @Test
    void twelveMonthlyCreditsAddUpForAnAllowanceThatDividesUnevenlyToo() {
        for (int allowance : new int[] {1, 7, 10, 13, 25, 30}) {
            int total = 0;
            for (int month = 1; month <= 12; month++) {
                total += LeaveAccrualService.monthlyCredit(allowance, month);
            }
            assertThat(total).as("allowance %d", allowance).isEqualTo(allowance);
        }
    }

    @Test
    void aTypeWithoutAnAllowanceAccruesNothing() {
        assertThat(LeaveAccrualService.monthlyCredit(0, 6)).isZero();
    }
}
