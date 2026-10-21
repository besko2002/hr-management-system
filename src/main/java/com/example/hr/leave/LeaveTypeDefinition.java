package com.example.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Reference row of the seeded {@code leave_types} table: whether the type is paid, whether
 * it consumes a balance and how many days the yearly allowance is. Read-only — there is no
 * CRUD for leave types, they are part of the schema (V2 migration).
 */
@Entity
@Table(name = "leave_types")
@Getter
public class LeaveTypeDefinition {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 20)
    private LeaveType code;

    @Column(name = "paid", nullable = false)
    private boolean paid;

    @Column(name = "requires_balance", nullable = false)
    private boolean requiresBalance;

    @Column(name = "annual_allowance_days", nullable = false)
    private int annualAllowanceDays;

    protected LeaveTypeDefinition() {
    }
}
