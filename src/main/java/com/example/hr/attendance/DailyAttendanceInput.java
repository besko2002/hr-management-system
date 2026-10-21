package com.example.hr.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The complete, immutable input of one day's derivation. Everything the calculator needs
 * is in here — no database, no clock, no Spring — which is what makes the rules
 * exhaustively unit-testable.
 *
 * @param now reference instant (in the attendance zone) used only to decide whether an
 *            open session is "still in" or an abandoned MISSING_CHECKOUT
 */
public record DailyAttendanceInput(
        LocalDate day,
        DayKind dayKind,
        LeaveCoverage leave,
        List<SessionWindow> sessions,
        LocalDateTime now) {

    public DailyAttendanceInput {
        if (day == null || dayKind == null || now == null) {
            throw new IllegalArgumentException("day, dayKind and now are required");
        }
        leave = leave == null ? LeaveCoverage.NONE : leave;
        List<SessionWindow> copy = sessions == null ? List.of() : new java.util.ArrayList<>(sessions);
        copy.sort(Comparator.comparing(SessionWindow::checkIn));
        sessions = Collections.unmodifiableList(copy);
    }

    public static DailyAttendanceInput of(LocalDate day, DayKind kind, SessionWindow... sessions) {
        return new DailyAttendanceInput(day, kind, LeaveCoverage.NONE, List.of(sessions),
                day.atTime(23, 59));
    }
}
