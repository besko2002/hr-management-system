package com.example.hr.payroll;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * One monthly payroll run. The {@code (run_year, run_month)} unique constraint is the real
 * idempotency guard — not an {@code exists} check — so two concurrent creations can never
 * both succeed.
 *
 * <p>Envers records creation and finalization (status / finalized_by / finalized_at).
 */
@Entity
@Table(name = "payroll_runs")
@Audited
@Getter
public class PayrollRun {

    @Id
    private UUID id;

    @Column(name = "run_year", nullable = false, updatable = false)
    private int year;

    @Column(name = "run_month", nullable = false, updatable = false)
    private int month;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayrollRunStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Employee createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalized_by")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Employee finalizedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @NotAudited
    @Version
    @Column(nullable = false)
    private Long version;

    protected PayrollRun() {
    }

    public PayrollRun(YearMonth period, Employee createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.year = period.getYear();
        this.month = period.getMonthValue();
        this.status = PayrollRunStatus.DRAFT;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public YearMonth period() {
        return YearMonth.of(year, month);
    }

    public boolean isDraft() {
        return status == PayrollRunStatus.DRAFT;
    }

    /** Refuses with 409 anything that would change a closed run. */
    public void requireDraft(String action) {
        if (!isDraft()) {
            throw new ConflictException("Payroll run " + period() + " is FINALIZED and cannot be " + action);
        }
    }

    public void finalizeRun(Employee by, Instant now) {
        requireDraft("finalized again");
        this.status = PayrollRunStatus.FINALIZED;
        this.finalizedBy = by;
        this.finalizedAt = now;
    }
}
