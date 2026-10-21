package com.example.hr.attendance;

import com.example.hr.employee.Employee;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads the raw material (sessions, calendar, approved leave) and runs the pure
 * {@link DailyAttendanceCalculator} over it. No access control lives here — that is
 * {@link AttendanceService}'s job — so payroll can reuse exactly the same derivation.
 *
 * <p>Two clamps apply to every range:
 * <ul>
 *   <li><b>today</b> — a day after today is not emitted at all. Nothing is known about it
 *       yet, and calling a future working day "ABSENT" would be a lie that payroll would
 *       then deduct.</li>
 *   <li><b>the employment window</b> — days before the hire date, and days after a
 *       termination date, are not emitted either.</li>
 * </ul>
 */
@Component
public class AttendanceDeriver {

    private final AttendanceSessionRepository sessions;
    private final WorkCalendar calendar;
    private final DailyAttendanceCalculator calculator;
    private final AttendanceRules rules;
    private final Clock clock;

    AttendanceDeriver(AttendanceSessionRepository sessions, WorkCalendar calendar,
                      DailyAttendanceCalculator calculator, AttendanceRules rules, Clock clock) {
        this.sessions = sessions;
        this.calendar = calendar;
        this.calculator = calculator;
        this.rules = rules;
        this.clock = clock;
    }

    /** The current instant from the injected clock — the single source of "now". */
    public java.time.Instant instant() {
        return clock.instant();
    }

    /** "Now" in the configured attendance zone. */
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), rules.zone());
    }

    public LocalDate today() {
        return now().toLocalDate();
    }

    public AttendanceRules rules() {
        return rules;
    }

    @Transactional(readOnly = true)
    public List<DailyAttendance> derive(Employee employee, LocalDate from, LocalDate to) {
        return deriveAll(List.of(employee), from, to)
                .getOrDefault(employee.getId(), List.of());
    }

    /** One pass over the whole team: three queries total, never per employee. */
    @Transactional(readOnly = true)
    public Map<UUID, List<DailyAttendance>> deriveAll(Collection<Employee> employees, LocalDate from,
                                                      LocalDate to) {
        Map<UUID, List<DailyAttendance>> result = new LinkedHashMap<>();
        LocalDate last = min(to, today());
        if (employees.isEmpty() || last.isBefore(from)) {
            employees.forEach(employee -> result.put(employee.getId(), List.of()));
            return result;
        }

        List<UUID> ids = employees.stream().map(Employee::getId).toList();
        var holidays = calendar.holidaysBetween(from, last);
        var coverage = calendar.leaveCoverage(ids, from, last);
        Map<UUID, Map<LocalDate, List<SessionWindow>>> byEmployee = loadSessions(ids, from, last);
        LocalDateTime now = now();

        for (Employee employee : employees) {
            LocalDate start = max(from, employee.getHireDate());
            LocalDate end = employee.getTerminatedAt() == null ? last : min(last, employee.getTerminatedAt());
            Map<LocalDate, List<SessionWindow>> windows =
                    byEmployee.getOrDefault(employee.getId(), Map.of());
            Map<LocalDate, LeaveCoverage> leave = coverage.getOrDefault(employee.getId(), Map.of());

            List<DailyAttendance> days = new ArrayList<>();
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                days.add(calculator.calculate(new DailyAttendanceInput(
                        day,
                        calendar.kindOf(day, holidays),
                        leave.getOrDefault(day, LeaveCoverage.NONE),
                        windows.getOrDefault(day, List.of()),
                        now)));
            }
            result.put(employee.getId(), days);
        }
        return result;
    }

    private Map<UUID, Map<LocalDate, List<SessionWindow>>> loadSessions(Collection<UUID> ids, LocalDate from,
                                                                        LocalDate to) {
        Map<UUID, Map<LocalDate, List<SessionWindow>>> byEmployee = new HashMap<>();
        for (AttendanceSession session : sessions.findForEmployees(ids, from, to)) {
            byEmployee.computeIfAbsent(session.employeeId(), key -> new HashMap<>())
                    .computeIfAbsent(session.getWorkDate(), key -> new ArrayList<>())
                    .add(session.toWindow(rules.zone()));
        }
        return byEmployee;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
