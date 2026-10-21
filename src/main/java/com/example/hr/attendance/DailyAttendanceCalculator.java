package com.example.hr.attendance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Turns the raw sessions of one employee-day into the figures the API and payroll use.
 * Pure: constructed from {@link AttendanceRules}, given a {@link DailyAttendanceInput},
 * returns a {@link DailyAttendance}. No database, no clock, no Spring.
 *
 * <h2>The rules, exactly</h2>
 * <ul>
 *   <li><b>workedMinutes</b> = the sum of the <em>closed</em> sessions. An open session
 *       contributes nothing: how long somebody has been in is not yet a worked figure.</li>
 *   <li><b>lateMinutes</b> = {@code max(0, firstIn − (workStart + grace))}, and only on a
 *       WORKING day. Being late on a weekend or a holiday is meaningless. Arriving exactly
 *       on the grace boundary is <em>not</em> late (the threshold is inclusive).
 *       Lateness is reported, never deducted.</li>
 *   <li><b>overtimeMinutes</b> on a WORKING day = the minutes of closed sessions that fall
 *       after {@code workEnd}. On a WEEKEND or HOLIDAY the whole of {@code workedMinutes}
 *       is overtime — such work is voluntary by definition and is paid at the holiday
 *       multiplier (see the payroll module). Both are capped at
 *       {@code maxOvertimeMinutesPerDay}.</li>
 *   <li><b>status</b>, in precedence order:
 *     <ol>
 *       <li>an open session older than {@code autoCloseAfterHours} → MISSING_CHECKOUT
 *           (the day is an anomaly: no worked minutes, no overtime, HR must correct it);</li>
 *       <li>any session at all → WEEKEND / HOLIDAY on a rest day, otherwise LATE when
 *           {@code lateMinutes > 0} and PRESENT when not. A still-open session on the
 *           current day lands here: the employee is simply in.</li>
 *       <li>no session → WEEKEND / HOLIDAY on a rest day; on a working day ON_LEAVE when
 *           an APPROVED leave request covers it, else ABSENT.</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>A day that is both covered by leave and worked counts as worked (PRESENT/LATE); the
 * leave coverage is still reported in {@link DailyAttendance#leave()}, but payroll only
 * deducts an UNPAID day when nobody actually turned up.
 */
public final class DailyAttendanceCalculator {

    private final AttendanceRules rules;

    public DailyAttendanceCalculator(AttendanceRules rules) {
        if (rules == null) {
            throw new IllegalArgumentException("rules are required");
        }
        this.rules = rules;
    }

    public AttendanceRules rules() {
        return rules;
    }

    public DailyAttendance calculate(DailyAttendanceInput input) {
        List<SessionWindow> sessions = input.sessions();
        boolean openSession = sessions.stream().anyMatch(SessionWindow::isOpen);
        boolean abandoned = sessions.stream().anyMatch(session -> isAbandoned(session, input.now()));

        LocalDateTime firstIn = sessions.isEmpty() ? null : sessions.get(0).checkIn();
        LocalDateTime lastOut = sessions.stream()
                .map(SessionWindow::checkOut)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        if (abandoned) {
            // Deliberately zero: an unclosed past session must never be guessed into pay.
            return new DailyAttendance(input.day(), input.dayKind(), AttendanceStatus.MISSING_CHECKOUT,
                    input.leave(), firstIn, lastOut, sessions.size(), 0, 0, 0, true);
        }

        int workedMinutes = 0;
        int rawOvertime = 0;
        LocalDateTime endOfWork = input.day().atTime(rules.workEnd());
        for (SessionWindow session : sessions) {
            if (session.isOpen()) {
                continue;
            }
            workedMinutes += minutes(session.checkIn(), session.checkOut());
            rawOvertime += input.dayKind().isRestDay()
                    ? minutes(session.checkIn(), session.checkOut())
                    : minutesAfter(session, endOfWork);
        }
        int overtimeMinutes = Math.min(rawOvertime, rules.maxOvertimeMinutesPerDay());
        int lateMinutes = lateMinutes(input, firstIn);

        AttendanceStatus status = status(input, sessions.isEmpty(), lateMinutes);
        return new DailyAttendance(input.day(), input.dayKind(), status, input.leave(), firstIn, lastOut,
                sessions.size(), workedMinutes, lateMinutes, overtimeMinutes, openSession);
    }

    private boolean isAbandoned(SessionWindow session, LocalDateTime now) {
        return session.isOpen()
                && Duration.between(session.checkIn(), now).toHours() >= rules.autoCloseAfterHours();
    }

    private int lateMinutes(DailyAttendanceInput input, LocalDateTime firstIn) {
        if (firstIn == null || input.dayKind().isRestDay()) {
            return 0;
        }
        LocalDateTime threshold = input.day().atTime(rules.lateThreshold());
        return (int) Math.max(0, Duration.between(threshold, firstIn).toMinutes());
    }

    private AttendanceStatus status(DailyAttendanceInput input, boolean noSessions, int lateMinutes) {
        if (input.dayKind() == DayKind.HOLIDAY) {
            return AttendanceStatus.HOLIDAY;
        }
        if (input.dayKind() == DayKind.WEEKEND) {
            return AttendanceStatus.WEEKEND;
        }
        if (noSessions) {
            return input.leave().covers() ? AttendanceStatus.ON_LEAVE : AttendanceStatus.ABSENT;
        }
        return lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
    }

    private static int minutes(LocalDateTime from, LocalDateTime to) {
        return (int) Duration.between(from, to).toMinutes();
    }

    /** The part of a closed session that lies after {@code boundary}. */
    private static int minutesAfter(SessionWindow session, LocalDateTime boundary) {
        LocalDateTime from = session.checkIn().isAfter(boundary) ? session.checkIn() : boundary;
        return session.checkOut().isAfter(from) ? minutes(from, session.checkOut()) : 0;
    }
}
