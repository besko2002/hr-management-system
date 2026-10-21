package com.example.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Proof that one employee was credited for one (year, month). The unique key
 * (employee, type, year, month) is what makes the accrual idempotent: the insert is an
 * {@code on conflict do nothing}, and the entitlement is raised only when the insert
 * actually created a row.
 */
@Entity
@Table(name = "accrual_log")
@Getter
public class AccrualLogEntry {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType leaveType;

    @Column(name = "accrual_year", nullable = false)
    private int year;

    @Column(name = "accrual_month", nullable = false)
    private int month;

    @Column(name = "days", nullable = false)
    private int days;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccrualLogEntry() {
    }
}
