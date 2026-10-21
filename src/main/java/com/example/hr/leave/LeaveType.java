package com.example.hr.leave;

/**
 * The kinds of leave an employee can ask for. The behavioural attributes (paid,
 * balance-bound, yearly allowance) live in the seeded {@code leave_types} table, which is
 * the single source of truth — see {@link LeaveTypeDefinition}.
 */
public enum LeaveType {
    ANNUAL,
    SICK,
    UNPAID
}
