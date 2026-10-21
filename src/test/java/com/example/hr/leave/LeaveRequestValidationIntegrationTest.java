package com.example.hr.leave;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every rule that can refuse a new leave request, one test each. */
class LeaveRequestValidationIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private String staffToken;
    private NewEmployee manager;
    private NewEmployee staff;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, null);
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        staffToken = login(staff.email(), staff.password());
    }

    // ------------------------------------------------------------------ the happy path

    @Test
    void anActiveEmployeeCanFileARequestForThemself() throws Exception {
        LocalDate start = planningStart();
        LocalDate end = endOf(start, 5);

        JsonNode created = body(fileRequest(staffToken, LeaveType.ANNUAL, start, end, "Family trip")
                .andExpect(status().isCreated()));

        assertThat(created.path("employeeId").asText()).isEqualTo(staff.id().toString());
        assertThat(created.path("employeeName").asText()).isEqualTo("Stan Staff");
        assertThat(created.path("status").asText()).isEqualTo("PENDING");
        assertThat(created.path("workingDays").asInt()).isEqualTo(5);
        assertThat(created.path("year").asInt()).isEqualTo(planningYear());
        assertThat(created.path("reason").asText()).isEqualTo("Family trip");
        assertThat(created.path("decidedById").isNull()).isTrue();
    }

    @Test
    void filingReservesTheDaysAsPendingWithoutUsingThem() throws Exception {
        int before = remaining(staffToken, LeaveType.ANNUAL, planningYear());
        fileDaysOk(staffToken, LeaveType.ANNUAL, planningStart(), 5);

        JsonNode balance = balance(staffToken, LeaveType.ANNUAL, planningYear());
        assertThat(balance.path("pendingDays").asInt()).isEqualTo(5);
        assertThat(balance.path("usedDays").asInt()).isZero();
        assertThat(balance.path("remainingDays").asInt()).isEqualTo(before - 5);
    }

    @Test
    void aSingleDayRequestIsAllowed() throws Exception {
        LocalDate start = planningStart();
        body(fileRequest(staffToken, LeaveType.ANNUAL, start, start).andExpect(status().isCreated()));
        assertThat(balance(staffToken, LeaveType.ANNUAL, planningYear()).path("pendingDays").asInt()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ date rules

    @Test
    void anEndDateBeforeTheStartDateIsRejected() throws Exception {
        LocalDate start = planningStart();
        fileRequest(staffToken, LeaveType.ANNUAL, start, start.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("endDate must not be before startDate"));
    }

    @Test
    void aStartDateInAPastYearIsRejected() throws Exception {
        int lastYear = LocalDate.now().getYear() - 1;
        fileRequest(staffToken, LeaveType.ANNUAL, LocalDate.of(lastYear, 6, 2), LocalDate.of(lastYear, 6, 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Leave cannot be requested for a past year"));
    }

    @Test
    void aStartDateBeforeTheHireDateIsRejected() throws Exception {
        // Hired two months after the planning window opens, so the request is in the
        // current year (not a past year) yet still before the hire date.
        LocalDate hireDate = planningStart().plusMonths(2);
        NewEmployee newJoiner = createEmployee(admin, "Nina Newjoiner", Role.EMPLOYEE, manager.id(), null,
                "4000.00", hireDate.toString());
        String token = login(newJoiner.email(), newJoiner.password());

        LocalDate start = planningStart();
        fileRequest(token, LeaveType.ANNUAL, start, endOf(start, 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Leave cannot start before the hire date (" + hireDate + ")"));
    }

    @Test
    void aStartDateMoreThanOneYearAheadIsRejected() throws Exception {
        LocalDate start = LocalDate.now().plusYears(1).plusMonths(2).withDayOfMonth(10);
        fileRequest(staffToken, LeaveType.ANNUAL, start, start.plusDays(2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Leave cannot start more than one year ahead"));
    }

    @Test
    void aRequestSpanningTwoCalendarYearsIsRejected() throws Exception {
        int year = LocalDate.now().getYear();
        fileRequest(staffToken, LeaveType.ANNUAL, LocalDate.of(year, 12, 29), LocalDate.of(year + 1, 1, 4))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "A leave request must not span two calendar years; file one request per year"));
    }

    @Test
    void aRangeOfOnlyWeekendDaysIsRejected() throws Exception {
        LocalDate friday = planningStart();
        while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
            friday = friday.plusDays(1);
        }
        fileRequest(staffToken, LeaveType.ANNUAL, friday, friday.plusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no working days")));
    }

    @Test
    void aRangeOfOnlyHolidaysIsRejected() throws Exception {
        LocalDate day = planningStart();
        addHolidayOk(admin, day, "Company day");
        fileRequest(staffToken, LeaveType.ANNUAL, day, day)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no working days")));
    }

    @Test
    void aHolidayInsideTheRangeReducesTheWorkingDays() throws Exception {
        LocalDate start = planningStart();
        LocalDate end = endOf(start, 5);
        addHolidayOk(admin, nextWorkingDayAfter(start), "Mid-range holiday");

        body(fileRequest(staffToken, LeaveType.ANNUAL, start, end).andExpect(status().isCreated()));
        assertThat(balance(staffToken, LeaveType.ANNUAL, planningYear()).path("pendingDays").asInt()).isEqualTo(4);
    }

    // ------------------------------------------------------------------ body rules

    @Test
    void aMissingTypeIsRejected() throws Exception {
        LocalDate start = planningStart();
        fileRequest(staffToken, null, start, start).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.type").exists());
    }

    @Test
    void missingDatesAreRejected() throws Exception {
        fileRequest(staffToken, LeaveType.ANNUAL, null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
    }

    @Test
    void anUnknownLeaveTypeIsRejected() throws Exception {
        LocalDate start = planningStart();
        mvc.perform(post("/api/leave/requests").header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "SABBATICAL",
                                "startDate", start.toString(), "endDate", start.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aReasonOfExactlyFiveHundredCharactersIsAccepted() throws Exception {
        LocalDate start = planningStart();
        fileRequest(staffToken, LeaveType.ANNUAL, start, start, "r".repeat(500))
                .andExpect(status().isCreated());
    }

    @Test
    void aReasonLongerThanFiveHundredCharactersIsRejected() throws Exception {
        LocalDate start = planningStart();
        fileRequest(staffToken, LeaveType.ANNUAL, start, start, "r".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").value("reason must be at most 500 characters"));
    }

    // ------------------------------------------------------------------ balance rules

    @Test
    void moreDaysThanTheRemainingBalanceIsRejected() throws Exception {
        // SICK is 10 days a year; ask for 11 working days.
        LocalDate start = planningStart();
        fileRequest(staffToken, LeaveType.SICK, start, endOf(start, 11))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Not enough SICK balance")));
    }

    @Test
    void exactlyTheRemainingBalanceIsAccepted() throws Exception {
        LocalDate start = planningStart();
        fileRequest(staffToken, LeaveType.SICK, start, endOf(start, 10)).andExpect(status().isCreated());
        assertThat(remaining(staffToken, LeaveType.SICK, planningYear())).isZero();
    }

    @Test
    void anAlreadyPendingReservationCountsAgainstTheBalance() throws Exception {
        LocalDate start = planningStart();
        fileDaysOk(staffToken, LeaveType.SICK, start, 8);
        LocalDate next = nextWorkingDayAfter(endOf(start, 8));
        fileRequest(staffToken, LeaveType.SICK, next, endOf(next, 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only 2 remaining")));
    }

    @Test
    void unpaidLeaveNeedsNoBalanceAtAll() throws Exception {
        LocalDate start = planningStart();
        fileRequest(staffToken, LeaveType.UNPAID, start, endOf(start, 15)).andExpect(status().isCreated());
        assertThat(balance(staffToken, LeaveType.UNPAID, planningYear()).path("remainingDays").isNull()).isTrue();
    }

    // ------------------------------------------------------------------ authentication

    @Test
    void anUnauthenticatedCallerCannotFileLeave() throws Exception {
        LocalDate start = planningStart();
        mvc.perform(post("/api/leave/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "ANNUAL",
                                "startDate", start.toString(), "endDate", start.toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTerminatedEmployeeCannotFileLeave() throws Exception {
        mvc.perform(post("/api/employees/" + staff.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        LocalDate start = planningStart();
        fileRequest(staffToken, LeaveType.ANNUAL, start, start).andExpect(status().isUnauthorized());
    }
}
