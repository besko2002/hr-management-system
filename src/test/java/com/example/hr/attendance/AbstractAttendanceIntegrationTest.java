package com.example.hr.attendance;

import com.example.hr.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared fixtures for the attendance and payroll API tests.
 *
 * <p>Historic attendance is built with HR corrections (the only way to write a session in
 * the past), always inside <b>March 2025</b> — a month that is permanently in the past, has
 * no holiday in the fixture database and, with the default Friday+Saturday weekend,
 * contains exactly <b>22 working days</b>. That makes every payroll figure derived from it
 * hand-computable.
 */
public abstract class AbstractAttendanceIntegrationTest extends AbstractIntegrationTest {

    /** The configured attendance zone, which the fixtures convert local times with. */
    protected static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    protected static final int MONTH_YEAR = 2025;
    protected static final int MONTH = 3;

    /** 2025-03-01 is a Saturday, so the month has 9 weekend days and 22 working days. */
    protected static final int WORKING_DAYS_IN_MARCH_2025 = 22;

    protected static LocalDate march(int day) {
        return LocalDate.of(MONTH_YEAR, MONTH, day);
    }

    protected static Instant at(LocalDate day, String localTime) {
        return day.atTime(LocalTime.parse(localTime)).atZone(ZONE).toInstant();
    }

    // ------------------------------------------------------------------ clocking

    protected ResultActions checkIn(String token) throws Exception {
        return mvc.perform(post("/api/attendance/check-in").header("Authorization", bearer(token)));
    }

    protected ResultActions checkOut(String token) throws Exception {
        return mvc.perform(post("/api/attendance/check-out").header("Authorization", bearer(token)));
    }

    // ------------------------------------------------------------------ HR corrections

    protected ResultActions addSession(String token, UUID employeeId, Instant checkIn, Instant checkOut,
                                       String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("checkIn", checkIn == null ? null : checkIn.toString());
        body.put("checkOut", checkOut == null ? null : checkOut.toString());
        body.put("reason", reason);
        return mvc.perform(post("/api/attendance/employees/" + employeeId + "/sessions")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    /** Adds a session that is expected to succeed and returns its id. */
    protected UUID addSessionOk(String token, UUID employeeId, LocalDate day, String from, String to)
            throws Exception {
        JsonNode created = body(addSession(token, employeeId, at(day, from),
                to == null ? null : at(day, to), "imported from the turnstile log")
                .andExpect(status().isCreated()));
        return UUID.fromString(created.path("id").asText());
    }

    protected ResultActions correctSession(String token, UUID sessionId, Instant checkIn, Instant checkOut,
                                           String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("checkIn", checkIn == null ? null : checkIn.toString());
        body.put("checkOut", checkOut == null ? null : checkOut.toString());
        body.put("reason", reason);
        return mvc.perform(post("/api/attendance/sessions/" + sessionId + "/correct")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    // ------------------------------------------------------------------ reading

    protected ResultActions myAttendance(String token, LocalDate from, LocalDate to) throws Exception {
        return mvc.perform(get("/api/attendance/me?from=" + from + "&to=" + to)
                .header("Authorization", bearer(token)));
    }

    protected ResultActions attendanceOf(String token, UUID employeeId, LocalDate from, LocalDate to)
            throws Exception {
        return mvc.perform(get("/api/attendance/employees/" + employeeId + "?from=" + from + "&to=" + to)
                .header("Authorization", bearer(token)));
    }

    protected JsonNode marchOf(String token, UUID employeeId) throws Exception {
        return body(attendanceOf(token, employeeId, march(1), march(31)).andExpect(status().isOk()));
    }

    /** One day out of an attendance range response. */
    protected JsonNode day(JsonNode range, LocalDate day) {
        for (JsonNode node : range.path("days")) {
            if (day.toString().equals(node.path("day").asText())) {
                return node;
            }
        }
        throw new AssertionError("The range does not contain " + day);
    }

    // ------------------------------------------------------------------ leave fixture

    /**
     * Inserts an APPROVED leave request straight into the database. The leave API only
     * accepts future dates by design, so a historic month cannot be built through it.
     */
    protected void approvedLeave(UUID employeeId, String leaveType, LocalDate from, LocalDate to,
                                 int workingDays) {
        jdbc.update("""
                insert into leave_requests (id, employee_id, leave_type, start_date, end_date, leave_year,
                                            working_days, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, 'APPROVED', now(), now(), 0)
                """, UUID.randomUUID(), employeeId, leaveType, from, to, from.getYear(), workingDays);
    }
}
