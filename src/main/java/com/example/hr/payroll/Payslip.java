package com.example.hr.payroll;

import com.example.hr.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An immutable snapshot of one employee's pay for one month. Nothing here is ever
 * recomputed on read: the identity fields, the inputs, the rates and every intermediate
 * are frozen at creation, and the full configuration in force (multipliers, insurance
 * band, tax brackets) plus the bracket-by-bracket tax breakdown are kept as JSON. A later
 * raise, a new tax table or an attendance correction therefore cannot rewrite history.
 */
@Entity
@Table(name = "payslips")
@Getter
public class Payslip {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, updatable = false)
    private PayrollRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "employee_number", nullable = false, length = 20)
    private String employeeNumber;

    @Column(name = "employee_name", nullable = false, length = 150)
    private String employeeName;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(name = "department_name", length = 120)
    private String departmentName;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "terminated_at")
    private LocalDate terminatedAt;

    @Column(name = "base_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "working_days_in_month", nullable = false)
    private int workingDaysInMonth;

    @Column(name = "payable_working_days", nullable = false)
    private int payableWorkingDays;

    @Column(name = "unpaid_leave_days", nullable = false)
    private int unpaidLeaveDays;

    @Column(name = "absent_days", nullable = false)
    private int absentDays;

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes;

    @Column(name = "holiday_minutes", nullable = false)
    private int holidayMinutes;

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "overtime_hours", nullable = false, precision = 12, scale = 2)
    private BigDecimal overtimeHours;

    @Column(name = "holiday_hours", nullable = false, precision = 12, scale = 2)
    private BigDecimal holidayHours;

    @Column(name = "prorated_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal proratedBase;

    @Column(name = "overtime_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal overtimePay;

    @Column(name = "holiday_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal holidayPay;

    @Column(name = "unpaid_leave_deduction", nullable = false, precision = 12, scale = 2)
    private BigDecimal unpaidLeaveDeduction;

    @Column(name = "absence_deduction", nullable = false, precision = 12, scale = 2)
    private BigDecimal absenceDeduction;

    @Column(name = "gross_earnings", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossEarnings;

    @Column(name = "insurable_wage", nullable = false, precision = 12, scale = 2)
    private BigDecimal insurableWage;

    @Column(name = "insurance", nullable = false, precision = 12, scale = 2)
    private BigDecimal insurance;

    @Column(name = "personal_exemption", nullable = false, precision = 12, scale = 2)
    private BigDecimal personalExemption;

    @Column(name = "taxable_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableIncome;

    @Column(name = "tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal tax;

    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay;

    @Column(name = "net_floored", nullable = false)
    private boolean netFloored;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payslip() {
    }

    public Payslip(PayrollRun run, Employee employee, PayrollResult result, String snapshotJson, Instant now) {
        this.id = UUID.randomUUID();
        this.run = run;
        this.employee = employee;
        this.employeeNumber = employee.getEmployeeNumber();
        this.employeeName = employee.getFullName();
        this.jobTitle = employee.getJobTitle();
        this.departmentName = employee.getDepartment() == null ? null : employee.getDepartment().getName();
        this.hireDate = employee.getHireDate();
        this.terminatedAt = employee.getTerminatedAt();

        this.baseSalary = result.baseSalary();
        this.workingDaysInMonth = result.workingDaysInMonth();
        this.payableWorkingDays = result.payableWorkingDays();
        this.unpaidLeaveDays = result.unpaidLeaveDays();
        this.absentDays = result.absentDays();
        this.lateMinutes = result.lateMinutes();
        this.overtimeMinutes = result.overtimeMinutes();
        this.holidayMinutes = result.holidayMinutes();

        this.dailyRate = result.dailyRate();
        this.hourlyRate = result.hourlyRate();
        this.overtimeHours = result.overtimeHours();
        this.holidayHours = result.holidayHours();

        this.proratedBase = result.proratedBase();
        this.overtimePay = result.overtimePay();
        this.holidayPay = result.holidayPay();
        this.unpaidLeaveDeduction = result.unpaidLeaveDeduction();
        this.absenceDeduction = result.absenceDeduction();

        this.grossEarnings = result.grossEarnings();
        this.insurableWage = result.insurableWage();
        this.insurance = result.insurance();
        this.personalExemption = result.personalExemption();
        this.taxableIncome = result.taxableIncome();
        this.tax = result.tax();
        this.netPay = result.netPay();
        this.netFloored = result.netFloored();

        this.snapshot = snapshotJson;
        this.createdAt = now;
    }

    public UUID employeeId() {
        return employee.getId();
    }
}
