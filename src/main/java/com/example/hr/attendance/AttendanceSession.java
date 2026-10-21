package com.example.hr.attendance;

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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * One presence interval. Instants are stored in UTC (the Hibernate {@code jdbc.time_zone}
 * is UTC project-wide); {@code workDate} is the same check-in rendered in the configured
 * attendance zone, stored so that day queries and exports never depend on the server's
 * default zone.
 */
@Entity
@Table(name = "attendance_sessions")
@Getter
public class AttendanceSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "check_in", nullable = false)
    private Instant checkIn;

    @Column(name = "check_out")
    private Instant checkOut;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AttendanceSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_by")
    private Employee correctedBy;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected AttendanceSession() {
    }

    private AttendanceSession(Employee employee, Instant checkIn, Instant checkOut, LocalDate workDate,
                              AttendanceSource source, Employee correctedBy, String correctionReason) {
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.workDate = workDate;
        this.source = source;
        this.correctedBy = correctedBy;
        this.correctionReason = correctionReason;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** The employee's own check-in: open, no corrector, no reason. */
    public static AttendanceSession selfCheckIn(Employee employee, Instant at, ZoneId zone) {
        return new AttendanceSession(employee, at, null, at.atZone(zone).toLocalDate(),
                AttendanceSource.SELF, null, null);
    }

    /** A session HR added after the fact; the reason is mandatory. */
    public static AttendanceSession correction(Employee employee, Instant checkIn, Instant checkOut,
                                               ZoneId zone, Employee by, String reason) {
        return new AttendanceSession(employee, checkIn, checkOut, checkIn.atZone(zone).toLocalDate(),
                AttendanceSource.HR_CORRECTION, by, reason);
    }

    public UUID employeeId() {
        return employee.getId();
    }

    public boolean isOpen() {
        return checkOut == null;
    }

    public void close(Instant at) {
        this.checkOut = at;
    }

    /** HR edits both ends of an existing session; the row becomes an HR_CORRECTION. */
    public void correctTo(Instant checkIn, Instant checkOut, ZoneId zone, Employee by, String reason) {
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.workDate = checkIn.atZone(zone).toLocalDate();
        this.source = AttendanceSource.HR_CORRECTION;
        this.correctedBy = by;
        this.correctionReason = reason;
    }

    public SessionWindow toWindow(ZoneId zone) {
        return new SessionWindow(id,
                checkIn.atZone(zone).toLocalDateTime(),
                checkOut == null ? null : checkOut.atZone(zone).toLocalDateTime(),
                source);
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
