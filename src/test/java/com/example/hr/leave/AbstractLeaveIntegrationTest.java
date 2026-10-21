package com.example.hr.leave;

import com.example.hr.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared fixtures for the leave API tests.
 *
 * <p>All leave is planned in the month <em>after</em> the current one, so the dates are
 * always in the future, never before the fixture hire date (2024-01-15), never more than a
 * year ahead and never split across two calendar years — whichever day the suite happens
 * to run on.
 */
public abstract class AbstractLeaveIntegrationTest extends AbstractIntegrationTest {

    /** The same weekend the application is configured with by default. */
    protected static final WorkingDayCalculator CALENDAR =
            new WorkingDayCalculator(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));

    // ------------------------------------------------------------------ planning window

    /** The first working day of next month: the anchor every test plans from. */
    protected LocalDate planningStart() {
        return CALENDAR.nextWorkingDay(LocalDate.now().plusMonths(1).withDayOfMonth(1), List.of());
    }

    protected int planningYear() {
        return planningStart().getYear();
    }

    /** The inclusive end date of a range that starts on {@code start} and spans {@code days} working days. */
    protected LocalDate endOf(LocalDate start, int days) {
        return CALENDAR.endDateFor(start, days, List.of());
    }

    /** The first working day strictly after {@code day} — used to build adjacent ranges. */
    protected LocalDate nextWorkingDayAfter(LocalDate day) {
        return CALENDAR.nextWorkingDay(day.plusDays(1), List.of());
    }

    // ------------------------------------------------------------------ requests

    protected ResultActions fileRequest(String token, LeaveType type, LocalDate start, LocalDate end,
                                        String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type == null ? null : type.name());
        body.put("startDate", start == null ? null : start.toString());
        body.put("endDate", end == null ? null : end.toString());
        body.put("reason", reason);
        return mvc.perform(post("/api/leave/requests").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions fileRequest(String token, LeaveType type, LocalDate start, LocalDate end)
            throws Exception {
        return fileRequest(token, type, start, end, null);
    }

    /** Files a request that is expected to succeed and returns its id. */
    protected UUID fileRequestOk(String token, LeaveType type, LocalDate start, LocalDate end) throws Exception {
        JsonNode created = body(fileRequest(token, type, start, end).andExpect(status().isCreated()));
        return UUID.fromString(created.path("id").asText());
    }

    /** Files a request for {@code days} working days starting on {@code start}. */
    protected UUID fileDaysOk(String token, LeaveType type, LocalDate start, int days) throws Exception {
        return fileRequestOk(token, type, start, endOf(start, days));
    }

    protected ResultActions decide(String token, UUID requestId, String action, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("decisionNote", note);
        return mvc.perform(post("/api/leave/requests/" + requestId + "/" + action)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions approve(String token, UUID requestId) throws Exception {
        return decide(token, requestId, "approve", null);
    }

    protected ResultActions reject(String token, UUID requestId, String note) throws Exception {
        return decide(token, requestId, "reject", note);
    }

    protected ResultActions cancel(String token, UUID requestId) throws Exception {
        return decide(token, requestId, "cancel", null);
    }

    // ------------------------------------------------------------------ balances

    protected JsonNode balances(String token, Integer year) throws Exception {
        return body(getAs(token, "/api/leave/balances/me" + (year == null ? "" : "?year=" + year))
                .andExpect(status().isOk()));
    }

    /** One balance row of the caller, by type. */
    protected JsonNode balance(String token, LeaveType type, Integer year) throws Exception {
        for (JsonNode node : balances(token, year)) {
            if (type.name().equals(node.path("leaveType").asText())) {
                return node;
            }
        }
        throw new AssertionError("No " + type + " balance returned");
    }

    protected int remaining(String token, LeaveType type, Integer year) throws Exception {
        return balance(token, type, year).path("remainingDays").asInt();
    }

    // ------------------------------------------------------------------ holidays

    protected ResultActions addHoliday(String token, LocalDate date, String name) throws Exception {
        return mvc.perform(post("/api/leave/holidays").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("date", date.toString(), "name", name))));
    }

    protected UUID addHolidayOk(String token, LocalDate date, String name) throws Exception {
        return UUID.fromString(
                body(addHoliday(token, date, name).andExpect(status().isCreated())).path("id").asText());
    }

    protected ResultActions updateHoliday(String token, UUID id, LocalDate date, String name) throws Exception {
        return mvc.perform(put("/api/leave/holidays/" + id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("date", date.toString(), "name", name))));
    }

    protected ResultActions deleteHoliday(String token, UUID id) throws Exception {
        return mvc.perform(delete("/api/leave/holidays/" + id).header("Authorization", bearer(token)));
    }

    // ------------------------------------------------------------------ accrual

    protected ResultActions runAccrual(String token, int year, int month) throws Exception {
        return mvc.perform(post("/api/leave/accrual/run?year=" + year + "&month=" + month)
                .header("Authorization", bearer(token)));
    }
}
