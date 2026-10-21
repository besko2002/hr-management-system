package com.example.hr.attendance;

/**
 * Whether an APPROVED leave request covers a day, and whether it is paid. Payroll needs
 * the distinction: a paid leave day costs nothing, an UNPAID one is deducted at the daily
 * rate.
 */
public enum LeaveCoverage {
    NONE,
    PAID,
    UNPAID;

    public boolean covers() {
        return this != NONE;
    }
}
