package com.example.hr.employee;

import com.example.hr.department.Department;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An employee account. The password is stored only as a BCrypt hash and the e-mail is
 * always stored lower-cased (the database also has a unique index on {@code lower(email)}).
 *
 * <p>Envers audits salary, title, manager, status, role, department and the other
 * non-secret columns. The password hash is never written to the audit table.
 */
@Entity
@Table(name = "employees")
@Audited
@Getter
public class Employee {

    @Id
    private UUID id;

    @Column(name = "employee_number", nullable = false, updatable = false, length = 20)
    private String employeeNumber;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String email;

    @NotAudited
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Employee manager;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal salary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status;

    /**
     * The date the employee left, set by the terminate flow from the injected
     * {@link java.time.Clock}. Null while ACTIVE — and also null for rows that were
     * terminated before the Phase 3 migration added the column.
     */
    @Column(name = "terminated_at")
    private LocalDate terminatedAt;

    @NotAudited
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @NotAudited
    @Version
    @Column(nullable = false)
    private Long version;

    protected Employee() {
    }

    public Employee(String employeeNumber, String fullName, String email, String passwordHash, Role role,
                    String jobTitle, Department department, Employee manager, LocalDate hireDate,
                    BigDecimal salary, boolean mustChangePassword) {
        this.id = UUID.randomUUID();
        this.employeeNumber = employeeNumber;
        this.fullName = fullName;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.jobTitle = jobTitle;
        this.department = department;
        this.manager = manager;
        this.hireDate = hireDate;
        this.salary = salary;
        this.status = EmployeeStatus.ACTIVE;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == EmployeeStatus.ACTIVE;
    }

    public void rename(String fullName) {
        this.fullName = fullName;
    }

    public void changeEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public void changeJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public void changeDepartment(Department department) {
        this.department = department;
    }

    public void changeManager(Employee manager) {
        this.manager = manager;
    }

    public void changeSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void changeHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    /** Terminates the employee on {@code effectiveDate}; payroll pro-rates the final month to it. */
    public void terminate(LocalDate effectiveDate) {
        this.status = EmployeeStatus.TERMINATED;
        this.terminatedAt = effectiveDate;
    }

    public void setPassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
    }

    /** The manager's id without forcing the lazy association to load a full row. */
    public UUID managerId() {
        return manager == null ? null : manager.getId();
    }
}
