package com.example.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * One employee's balance of one leave type in one year.
 *
 * <p>{@code remaining} is <em>never stored</em>: it is always
 * {@code entitled + carriedOver - used - pending}. {@code pending} holds the days
 * reserved by PENDING requests, so a second request cannot spend days that a still
 * undecided one has already claimed.
 *
 * <p>The row carries an optimistic {@code version} as a second line of defence; the
 * primary concurrency control is the {@code SELECT … FOR UPDATE} taken by
 * {@link LeaveBalanceRepository#lockForUpdate(UUID, int)} before any arithmetic.
 */
@Entity
@Table(name = "leave_balances")
@Getter
public class LeaveBalance {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, updatable = false, length = 20)
    private LeaveType leaveType;

    @Column(name = "balance_year", nullable = false, updatable = false)
    private int year;

    @Column(name = "entitled_days", nullable = false)
    private int entitledDays;

    @Column(name = "carried_over_days", nullable = false)
    private int carriedOverDays;

    @Column(name = "used_days", nullable = false)
    private int usedDays;

    @Column(name = "pending_days", nullable = false)
    private int pendingDays;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected LeaveBalance() {
    }

    public LeaveBalance(UUID employeeId, LeaveType leaveType, int year, int entitledDays, int carriedOverDays) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.leaveType = leaveType;
        this.year = year;
        this.entitledDays = entitledDays;
        this.carriedOverDays = carriedOverDays;
        this.usedDays = 0;
        this.pendingDays = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Computed, never persisted. */
    public int remainingDays() {
        return entitledDays + carriedOverDays - usedDays - pendingDays;
    }

    /** A new PENDING request claims days. */
    public void reserve(int days) {
        requirePositive(days);
        this.pendingDays += days;
    }

    /** A PENDING request was rejected or cancelled: give the days back. */
    public void releaseReserved(int days) {
        requirePositive(days);
        if (days > pendingDays) {
            throw new IllegalStateException("Cannot release " + days + " reserved days; only " + pendingDays
                    + " are reserved");
        }
        this.pendingDays -= days;
    }

    /** Approval: the reservation becomes consumption. */
    public void consumeReserved(int days) {
        releaseReserved(days);
        this.usedDays += days;
    }

    /** Cancelling an approved, not yet started leave: give the consumed days back. */
    public void releaseUsed(int days) {
        requirePositive(days);
        if (days > usedDays) {
            throw new IllegalStateException("Cannot refund " + days + " used days; only " + usedDays + " are used");
        }
        this.usedDays -= days;
    }

    /** Monthly accrual (or any manual grant) raises the entitlement. */
    public void credit(int days) {
        requirePositive(days);
        this.entitledDays += days;
    }

    private static void requirePositive(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be at least 1 but was " + days);
        }
    }
}
