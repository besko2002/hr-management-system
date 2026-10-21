package com.example.hr.attendance;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One presence interval, already rendered in the configured attendance zone — the pure
 * calculator never sees an {@code Instant} or a {@code ZoneId}.
 *
 * @param checkOut {@code null} while the session is still open
 */
public record SessionWindow(
        UUID id,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        AttendanceSource source) {

    public SessionWindow {
        if (checkIn == null) {
            throw new IllegalArgumentException("checkIn is required");
        }
        if (checkOut != null && !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
    }

    /** Convenience for tests: a closed session on {@code day} between two wall-clock times. */
    public static SessionWindow of(java.time.LocalDate day, String from, String to) {
        return new SessionWindow(null, day.atTime(java.time.LocalTime.parse(from)),
                to == null ? null : day.atTime(java.time.LocalTime.parse(to)), AttendanceSource.SELF);
    }

    public boolean isOpen() {
        return checkOut == null;
    }
}
