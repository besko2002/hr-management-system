package com.example.hr.attendance;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.attendance.AttendanceDtos.AttendanceRangeResponse;
import com.example.hr.attendance.AttendanceDtos.CorrectSessionBody;
import com.example.hr.attendance.AttendanceDtos.DayResponse;
import com.example.hr.attendance.AttendanceDtos.SessionResponse;
import com.example.hr.attendance.AttendanceDtos.TeamTodayEntry;
import com.example.hr.attendance.AttendanceDtos.TeamTodayResponse;
import com.example.hr.common.BadRequestException;
import com.example.hr.common.ConflictException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.employee.EmployeeStatus;
import com.example.hr.security.CurrentEmployee;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Attendance rules and access control.
 *
 * <h2>Check-in / check-out</h2>
 * Self-service only, and only for an ACTIVE employee. <b>One open session at a time within
 * a work day</b>: a second check-in on the same day is 409, a check-out with nothing open
 * today is 409. Several <em>closed</em> sessions a day are perfectly normal.
 *
 * <p>A session left open on an earlier day does <b>not</b> block today's check-in — it is
 * surfaced as MISSING_CHECKOUT on that earlier day and HR corrects it. It also cannot be
 * closed by today's check-out: that would invent hours.
 *
 * <h2>Corrections (HR/ADMIN only)</h2>
 * Both editing an existing session and inserting a missing one require a reason, stamp
 * {@code correctedBy} and flip the source to HR_CORRECTION. Refused: a check-out at or
 * before the check-in (400), a time in the future (400), a session longer than 24 hours
 * (400) and any overlap with another session of the same employee (409).
 *
 * <h2>Visibility</h2>
 * A day range is readable by the employee themself, by <em>any</em> ancestor manager and by
 * HR/ADMIN; anybody else gets 404 so ids cannot be probed. No attendance payload ever
 * contains a salary or a pay figure.
 */
@Service
public class AttendanceService {

    /** Three months of days is already a lot of JSON; longer ranges belong in the export. */
    private static final long MAX_RANGE_DAYS = 92;

    private static final Duration MAX_SESSION = Duration.ofHours(24);

    private final AttendanceSessionRepository sessions;
    private final AttendanceDeriver deriver;
    private final WorkCalendar calendar;
    private final EmployeeRepository employees;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;
    private final AttendanceRules rules;

    AttendanceService(AttendanceSessionRepository sessions, AttendanceDeriver deriver, WorkCalendar calendar,
                      EmployeeRepository employees, EmployeeAccessPolicy policy,
                      CurrentEmployee currentEmployee, AttendanceRules rules) {
        this.sessions = sessions;
        this.deriver = deriver;
        this.calendar = calendar;
        this.employees = employees;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
        this.rules = rules;
    }

    // ------------------------------------------------------------------ clocking

    @Transactional
    public SessionResponse checkIn() {
        Employee me = requireActive();
        Instant now = deriver.instant();
        LocalDate workDate = deriver.today();

        if (sessions.findOpenOn(me.getId(), workDate).isPresent()) {
            throw new ConflictException("You already have an open attendance session today; check out first");
        }
        return toSession(sessions.save(AttendanceSession.selfCheckIn(me, now, rules.zone())), rules.zone());
    }

    @Transactional
    public SessionResponse checkOut() {
        Employee me = requireActive();
        Instant now = deriver.instant();
        AttendanceSession open = sessions.findOpenOn(me.getId(), deriver.today())
                .orElseThrow(() -> new ConflictException(
                        "You have no open attendance session today; check in first"));
        if (!now.isAfter(open.getCheckIn())) {
            throw new ConflictException("The check-out time would not be after the check-in time");
        }
        open.close(now);
        return toSession(sessions.save(open), rules.zone());
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public AttendanceRangeResponse myDays(LocalDate from, LocalDate to) {
        Employee me = currentEmployee.require();
        return range(me, from, to);
    }

    @Transactional(readOnly = true)
    public AttendanceRangeResponse daysOf(UUID employeeId, LocalDate from, LocalDate to) {
        Employee actor = currentEmployee.require();
        Employee target = require(employeeId);
        // 404, not 403: an outsider must not learn that this id exists.
        policy.requireCanViewProfile(actor, target);
        return range(target, from, to);
    }

    /**
     * Today for the caller's descendants (HR/ADMIN: every ACTIVE employee). Who is in, who
     * is late, who is absent, who is on leave — in one pass, no N+1.
     */
    @Transactional(readOnly = true)
    public TeamTodayResponse teamToday() {
        Employee me = currentEmployee.require();
        List<Employee> team = me.getRole().isHrOrAdmin()
                ? employees.findAll().stream()
                        .filter(employee -> employee.getStatus() == EmployeeStatus.ACTIVE)
                        .sorted(Comparator.comparing(Employee::getFullName))
                        .toList()
                : descendantsOf(me);

        LocalDate today = deriver.today();
        Map<UUID, List<DailyAttendance>> derived = deriver.deriveAll(team, today, today);

        List<TeamTodayEntry> members = new ArrayList<>();
        int in = 0;
        int present = 0;
        int late = 0;
        int absent = 0;
        int onLeave = 0;
        int missing = 0;
        for (Employee employee : team) {
            List<DailyAttendance> days = derived.getOrDefault(employee.getId(), List.of());
            if (days.isEmpty()) {
                continue;
            }
            DailyAttendance day = days.get(0);
            boolean currentlyIn = day.openSession() && day.status() != AttendanceStatus.MISSING_CHECKOUT;
            if (currentlyIn) {
                in++;
            }
            switch (day.status()) {
                case PRESENT -> present++;
                case LATE -> late++;
                case ABSENT -> absent++;
                case ON_LEAVE -> onLeave++;
                case MISSING_CHECKOUT -> missing++;
                default -> {
                    // WEEKEND / HOLIDAY: counted only in teamSize.
                }
            }
            members.add(new TeamTodayEntry(employee.getId(), employee.getEmployeeNumber(),
                    employee.getFullName(), employee.getJobTitle(), day.status(), day.leave(),
                    day.firstIn(), day.lastOut(), currentlyIn, day.workedMinutes(), day.lateMinutes(),
                    day.overtimeMinutes()));
        }

        DayKind kind = calendar.kindOf(today, calendar.holidaysBetween(today, today));
        return new TeamTodayResponse(today, kind, members.size(), in, present, late, absent, onLeave,
                missing, members);
    }

    // ------------------------------------------------------------------ corrections

    @Transactional
    public SessionResponse correct(UUID sessionId, CorrectSessionBody body) {
        Employee actor = currentEmployee.require();
        policy.requireHrOrAdmin(actor, "correct attendance sessions");
        AttendanceSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("No attendance session with id " + sessionId));

        validate(body.checkIn(), body.checkOut());
        requireNoOverlap(session.employeeId(), body.checkIn(), body.checkOut(), session.getId());
        session.correctTo(body.checkIn(), body.checkOut(), rules.zone(), actor, body.reason().trim());
        return toSession(sessions.save(session), rules.zone());
    }

    @Transactional
    public SessionResponse addSession(UUID employeeId, CorrectSessionBody body) {
        Employee actor = currentEmployee.require();
        policy.requireHrOrAdmin(actor, "add attendance sessions");
        Employee target = require(employeeId);

        validate(body.checkIn(), body.checkOut());
        LocalDate workDate = body.checkIn().atZone(rules.zone()).toLocalDate();
        if (workDate.isBefore(target.getHireDate())) {
            throw new BadRequestException("The session starts before the employee's hire date ("
                    + target.getHireDate() + ")");
        }
        requireNoOverlap(employeeId, body.checkIn(), body.checkOut(),
                AttendanceSessionRepository.NOTHING_EXCLUDED);
        return toSession(sessions.save(AttendanceSession.correction(target, body.checkIn(), body.checkOut(),
                rules.zone(), actor, body.reason().trim())), rules.zone());
    }

    @Transactional(readOnly = true)
    public List<Employee> exportableEmployees() {
        policy.requireHrOrAdmin(currentEmployee.require(), "export attendance");
        return employees.findAll().stream()
                .sorted(Comparator.comparing(Employee::getEmployeeNumber))
                .toList();
    }

    // ------------------------------------------------------------------ internals

    private void validate(Instant checkIn, Instant checkOut) {
        Instant now = deriver.instant();
        if (checkIn.isAfter(now)) {
            throw new BadRequestException("checkIn must not be in the future");
        }
        if (checkOut != null) {
            if (!checkOut.isAfter(checkIn)) {
                throw new BadRequestException("checkOut must be strictly after checkIn");
            }
            if (checkOut.isAfter(now)) {
                throw new BadRequestException("checkOut must not be in the future");
            }
            if (Duration.between(checkIn, checkOut).compareTo(MAX_SESSION) > 0) {
                throw new BadRequestException("A session must not be longer than 24 hours");
            }
        }
    }

    private void requireNoOverlap(UUID employeeId, Instant checkIn, Instant checkOut, UUID excludeId) {
        // An open session is a point in time for overlap purposes: its end is unknown.
        Instant end = checkOut == null ? checkIn.plusMillis(1) : checkOut;
        if (sessions.overlaps(employeeId, checkIn, end, excludeId)) {
            throw new ConflictException(
                    "That interval overlaps another attendance session of the same employee");
        }
    }

    private AttendanceRangeResponse range(Employee employee, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BadRequestException("'to' must not be before 'from'");
        }
        long span = ChronoUnit.DAYS.between(from, to) + 1;
        if (span > MAX_RANGE_DAYS) {
            throw new BadRequestException("The range must not exceed " + MAX_RANGE_DAYS + " days");
        }

        List<DailyAttendance> days = deriver.derive(employee, from, to);
        List<SessionResponse> all = sessions
                .findByEmployeeIdAndWorkDateBetweenOrderByCheckInAsc(employee.getId(), from, to)
                .stream().map(session -> toSession(session, rules.zone())).toList();

        List<DayResponse> payload = new ArrayList<>();
        int workingDays = 0;
        int presentDays = 0;
        int lateDays = 0;
        int absentDays = 0;
        int leaveDays = 0;
        int unpaidLeaveDays = 0;
        int missingDays = 0;
        int workedMinutes = 0;
        int lateMinutes = 0;
        int overtimeMinutes = 0;
        int holidayMinutes = 0;
        for (DailyAttendance day : days) {
            if (day.dayKind() == DayKind.WORKING) {
                workingDays++;
            }
            switch (day.status()) {
                case PRESENT -> presentDays++;
                case LATE -> {
                    presentDays++;
                    lateDays++;
                }
                case ABSENT -> absentDays++;
                case ON_LEAVE -> {
                    leaveDays++;
                    if (day.leave() == LeaveCoverage.UNPAID) {
                        unpaidLeaveDays++;
                    }
                }
                case MISSING_CHECKOUT -> missingDays++;
                default -> {
                    // WEEKEND / HOLIDAY contribute only minutes.
                }
            }
            workedMinutes += day.workedMinutes();
            lateMinutes += day.lateMinutes();
            overtimeMinutes += day.weekdayOvertimeMinutes();
            holidayMinutes += day.restDayMinutes();
            payload.add(new DayResponse(day.day(), day.dayKind(), day.status(), day.leave(), day.firstIn(),
                    day.lastOut(), day.sessionCount(), day.workedMinutes(), day.lateMinutes(),
                    day.overtimeMinutes(), day.openSession(),
                    all.stream().filter(s -> s.workDate().equals(day.day())).toList()));
        }
        return new AttendanceRangeResponse(employee.getId(), employee.getFullName(), from, to, workingDays,
                presentDays, lateDays, absentDays, leaveDays, unpaidLeaveDays, missingDays, workedMinutes,
                lateMinutes, overtimeMinutes, holidayMinutes, payload);
    }

    private List<Employee> descendantsOf(Employee me) {
        List<Object[]> rows = employees.findDescendants(me.getId());
        List<UUID> ids = rows.stream().map(row -> (UUID) row[0]).toList();
        return ids.isEmpty() ? List.of() : employees.findAllById(ids).stream()
                .sorted(Comparator.comparing(Employee::getFullName))
                .toList();
    }

    private Employee requireActive() {
        Employee me = currentEmployee.require();
        if (!me.isActive()) {
            throw new ConflictException("A terminated employee cannot record attendance");
        }
        return me;
    }

    private Employee require(UUID id) {
        return employees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No employee with id " + id));
    }

    static SessionResponse toSession(AttendanceSession session, java.time.ZoneId zone) {
        SessionWindow window = session.toWindow(zone);
        Integer minutes = session.getCheckOut() == null ? null
                : (int) Duration.between(session.getCheckIn(), session.getCheckOut()).toMinutes();
        return new SessionResponse(session.getId(), session.employeeId(), session.getWorkDate(),
                session.getCheckIn(), session.getCheckOut(), window.checkIn(), window.checkOut(), minutes,
                session.getSource(),
                session.getCorrectedBy() == null ? null : session.getCorrectedBy().getId(),
                session.getCorrectionReason());
    }
}
