package com.example.hr.leave;

import com.example.hr.common.ConflictException;
import com.example.hr.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One leave request. {@code workingDays} is frozen at creation time: a later change to the
 * holiday calendar must not silently alter how many days an already filed request costs,
 * otherwise a refund could not return the balance to its exact previous numbers.
 *
 * <p>Envers records decisions (status / decided_by / decided_at / decision_note).
 */
@Entity
@Table(name = "leave_requests")
@Audited
@Getter
public class LeaveRequest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, updatable = false, length = 20)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    /** The calendar year the request is accounted against; a request never spans two years. */
    @Column(name = "leave_year", nullable = false, updatable = false)
    private int leaveYear;

    @Column(name = "working_days", nullable = false, updatable = false)
    private int workingDays;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeaveStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Employee decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @NotAudited
    @Version
    @Column(nullable = false)
    private Long version;

    protected LeaveRequest() {
    }

    public LeaveRequest(Employee employee, LeaveType leaveType, LocalDate startDate, LocalDate endDate,
                        int workingDays, String reason) {
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.leaveYear = startDate.getYear();
        this.workingDays = workingDays;
        this.reason = reason;
        this.status = LeaveStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID employeeId() {
        return employee.getId();
    }

    public void approve(Employee decider, String note) {
        transitionTo(LeaveStatus.APPROVED, decider, note);
    }

    public void reject(Employee decider, String note) {
        transitionTo(LeaveStatus.REJECTED, decider, note);
    }

    public void cancel(Employee decider, String note) {
        transitionTo(LeaveStatus.CANCELLED, decider, note);
    }

    /** Refuses every transition the state machine does not allow, with 409. */
    private void transitionTo(LeaveStatus target, Employee decider, String note) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("A " + status + " leave request cannot become " + target
                    + " (allowed from " + status + ": "
                    + (status.isTerminal() ? "nothing, it is final" : status.allowedTransitions()) + ")");
        }
        this.status = target;
        this.decidedBy = decider;
        this.decidedAt = Instant.now();
        this.decisionNote = note;
    }
}
