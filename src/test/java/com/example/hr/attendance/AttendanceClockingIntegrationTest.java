package com.example.hr.attendance;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Check-in / check-out state rules against the real database. */
class AttendanceClockingIntegrationTest extends AbstractAttendanceIntegrationTest {

    private String adminToken;
    private NewEmployee staff;
    private String staffToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        staff = createEmployee(adminToken, "Sara Staff", Role.EMPLOYEE, null);
        staffToken = login(staff.email(), staff.password());
    }

    @Test
    void checkInOpensASessionForTheCallerAndCheckOutClosesIt() throws Exception {
        JsonNode opened = body(checkIn(staffToken).andExpect(status().isCreated()));
        assertThat(opened.path("employeeId").asText()).isEqualTo(staff.id().toString());
        assertThat(opened.path("checkOut").isNull()).isTrue();
        assertThat(opened.path("source").asText()).isEqualTo("SELF");
        assertThat(opened.path("correctedById").isNull()).isTrue();

        JsonNode closed = body(checkOut(staffToken).andExpect(status().isOk()));
        assertThat(closed.path("id").asText()).isEqualTo(opened.path("id").asText());
        assertThat(closed.path("checkOut").isNull()).isFalse();
        assertThat(closed.path("minutes").asInt()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void aSecondCheckInWhileOneIsOpenIsRefusedWithConflict() throws Exception {
        checkIn(staffToken).andExpect(status().isCreated());
        checkIn(staffToken).andExpect(status().isConflict());

        Long rows = jdbc.queryForObject("select count(*) from attendance_sessions where employee_id = ?",
                Long.class, staff.id());
        assertThat(rows).isEqualTo(1L);
    }

    @Test
    void checkingOutWithoutAnOpenSessionIsRefusedWithConflict() throws Exception {
        checkOut(staffToken).andExpect(status().isConflict());
    }

    @Test
    void checkingOutTwiceIsRefusedTheSecondTime() throws Exception {
        checkIn(staffToken).andExpect(status().isCreated());
        checkOut(staffToken).andExpect(status().isOk());
        checkOut(staffToken).andExpect(status().isConflict());
    }

    @Test
    void severalClosedSessionsOnTheSameDayAreAllowed() throws Exception {
        checkIn(staffToken).andExpect(status().isCreated());
        checkOut(staffToken).andExpect(status().isOk());
        checkIn(staffToken).andExpect(status().isCreated());
        checkOut(staffToken).andExpect(status().isOk());

        Long rows = jdbc.queryForObject("select count(*) from attendance_sessions where employee_id = ?",
                Long.class, staff.id());
        assertThat(rows).isEqualTo(2L);
    }

    @Test
    void aSessionLeftOpenOnAnEarlierDayDoesNotBlockTodaysCheckIn() throws Exception {
        // An abandoned session from two days ago, written the only way the past can be
        // written: an HR correction with no check-out.
        LocalDate twoDaysAgo = LocalDate.now(ZONE).minusDays(2);
        addSession(adminToken, staff.id(), at(twoDaysAgo, "09:00"), null, "turnstile failure")
                .andExpect(status().isCreated());

        checkIn(staffToken).andExpect(status().isCreated());

        JsonNode range = body(myAttendance(staffToken, twoDaysAgo, LocalDate.now(ZONE))
                .andExpect(status().isOk()));
        assertThat(day(range, twoDaysAgo).path("status").asText()).isEqualTo("MISSING_CHECKOUT");
        assertThat(range.path("missingCheckoutDays").asInt()).isEqualTo(1);
    }

    @Test
    void anAbandonedSessionFromAnEarlierDayCannotBeClosedByTodaysCheckOut() throws Exception {
        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        addSession(adminToken, staff.id(), at(yesterday, "09:00"), null, "turnstile failure")
                .andExpect(status().isCreated());

        checkOut(staffToken).andExpect(status().isConflict());
        Long open = jdbc.queryForObject(
                "select count(*) from attendance_sessions where employee_id = ? and check_out is null",
                Long.class, staff.id());
        assertThat(open).isEqualTo(1L);
    }

    @Test
    void aTerminatedEmployeeCannotRecordAttendance() throws Exception {
        NewEmployee leaver = createEmployee(adminToken, "Leo Leaver", Role.EMPLOYEE, null);
        String leaverToken = login(leaver.email(), leaver.password());
        mvc.perform(post("/api/employees/" + leaver.id() + "/terminate")
                .header("Authorization", bearer(adminToken))).andExpect(status().isOk());

        // The security filter rejects a terminated account's token outright.
        checkIn(leaverToken).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousClockingIsRejected() throws Exception {
        mvc.perform(post("/api/attendance/check-in")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/attendance/check-out")).andExpect(status().isUnauthorized());
    }

    @Test
    void anEmployeeOnlyEverClocksForThemself() throws Exception {
        NewEmployee other = createEmployee(adminToken, "Omar Other", Role.EMPLOYEE, null);
        String otherToken = login(other.email(), other.password());

        checkIn(staffToken).andExpect(status().isCreated());
        // The other employee's own check-in is unaffected: sessions are strictly per caller.
        checkIn(otherToken).andExpect(status().isCreated());

        UUID staffId = staff.id();
        Long mine = jdbc.queryForObject("select count(*) from attendance_sessions where employee_id = ?",
                Long.class, staffId);
        assertThat(mine).isEqualTo(1L);
    }

    @Test
    void todaysOpenSessionReadsAsPresentNotAsAMissingCheckout() throws Exception {
        checkIn(staffToken).andExpect(status().isCreated());

        LocalDate today = LocalDate.now(ZONE);
        JsonNode range = body(myAttendance(staffToken, today, today).andExpect(status().isOk()));
        JsonNode today0 = day(range, today);
        assertThat(today0.path("openSession").asBoolean()).isTrue();
        assertThat(today0.path("status").asText()).isIn("PRESENT", "LATE", "WEEKEND", "HOLIDAY");
        assertThat(today0.path("workedMinutes").asInt()).isZero();
    }
}
