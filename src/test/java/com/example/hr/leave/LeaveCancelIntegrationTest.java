package com.example.hr.leave;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cancellation rules: always possible while PENDING, and for an APPROVED leave only while
 * it has not started yet. Every cancellation refunds the exact number of days.
 */
class LeaveCancelIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private String managerToken;
    private String staffToken;
    private String hrToken;
    private String unrelatedToken;
    private NewEmployee staff;

    private LocalDate start;
    private LocalDate end;
    private UUID request;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        NewEmployee manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, null);
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        NewEmployee hr = createEmployee(admin, "Hilda Hr", Role.HR, null);
        NewEmployee unrelated = createEmployee(admin, "Ulla Unrelated", Role.EMPLOYEE, null);

        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());
        hrToken = login(hr.email(), hr.password());
        unrelatedToken = login(unrelated.email(), unrelated.password());

        start = planningStart();
        end = endOf(start, 5);
        request = fileRequestOk(staffToken, LeaveType.ANNUAL, start, end);
    }

    /** A range that has certainly already started: inside the current year, up to 14 days ago. */
    private LocalDate startedRangeBegin() {
        LocalDate today = LocalDate.now();
        LocalDate twoWeeksAgo = today.minusDays(14);
        LocalDate januaryFirst = LocalDate.of(today.getYear(), 1, 1);
        return twoWeeksAgo.isBefore(januaryFirst) ? januaryFirst : twoWeeksAgo;
    }

    // ------------------------------------------------------------------ pending

    @Test
    void theRequesterCanCancelWhilePending() throws Exception {
        JsonNode cancelled = body(cancel(staffToken, request).andExpect(status().isOk()));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("decidedById").asText()).isEqualTo(staff.id().toString());
    }

    @Test
    void cancellingWhilePendingRefundsTheReservedDays() throws Exception {
        cancel(staffToken, request).andExpect(status().isOk());
        JsonNode balance = balance(staffToken, LeaveType.ANNUAL, planningYear());
        assertThat(balance.path("pendingDays").asInt()).isZero();
        assertThat(balance.path("usedDays").asInt()).isZero();
        assertThat(balance.path("remainingDays").asInt()).isEqualTo(21);
    }

    @Test
    void theDaysAreFreeForANewRequestAfterACancellation() throws Exception {
        cancel(staffToken, request).andExpect(status().isOk());
        fileRequest(staffToken, LeaveType.ANNUAL, start, end).andExpect(status().isCreated());
    }

    @Test
    void aPendingRequestThatAlreadyStartedCanStillBeCancelled() throws Exception {
        LocalDate begin = startedRangeBegin();
        UUID started = fileRequestOk(staffToken, LeaveType.ANNUAL, begin, begin.plusDays(6));
        cancel(staffToken, started).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ------------------------------------------------------------------ approved

    @Test
    void theRequesterCanCancelAnApprovedLeaveThatHasNotStartedYet() throws Exception {
        approve(managerToken, request).andExpect(status().isOk());
        cancel(staffToken, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        JsonNode balance = balance(staffToken, LeaveType.ANNUAL, planningYear());
        assertThat(balance.path("usedDays").asInt()).isZero();
        assertThat(balance.path("pendingDays").asInt()).isZero();
        assertThat(balance.path("remainingDays").asInt()).isEqualTo(21);
    }

    @Test
    void anApprovedLeaveThatAlreadyStartedCannotBeCancelled() throws Exception {
        LocalDate begin = startedRangeBegin();
        UUID started = fileRequestOk(staffToken, LeaveType.ANNUAL, begin, begin.plusDays(6));
        approve(managerToken, started).andExpect(status().isOk());

        cancel(staffToken, started)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "An approved leave can only be cancelled before it starts (it starts on " + begin + ")"));
    }

    @Test
    void theUsedDaysStayBookedWhenTheCancellationIsRefused() throws Exception {
        LocalDate begin = startedRangeBegin();
        UUID started = fileRequestOk(staffToken, LeaveType.ANNUAL, begin, begin.plusDays(6));
        int days = body(getAs(staffToken, "/api/leave/requests/" + started)).path("workingDays").asInt();
        approve(managerToken, started).andExpect(status().isOk());
        cancel(staffToken, started).andExpect(status().isConflict());

        assertThat(balance(staffToken, LeaveType.ANNUAL, begin.getYear()).path("usedDays").asInt())
                .isEqualTo(days);
    }

    @Test
    void evenHrCannotCancelAnApprovedLeaveThatAlreadyStarted() throws Exception {
        LocalDate begin = startedRangeBegin();
        UUID started = fileRequestOk(staffToken, LeaveType.ANNUAL, begin, begin.plusDays(6));
        approve(managerToken, started).andExpect(status().isOk());
        cancel(hrToken, started).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ authority

    @Test
    void hrCanCancelOnBehalfOfTheEmployee() throws Exception {
        cancel(hrToken, request).andExpect(status().isOk());
    }

    @Test
    void adminCanCancelOnBehalfOfTheEmployee() throws Exception {
        cancel(admin, request).andExpect(status().isOk());
    }

    @Test
    void theDirectManagerCannotCancelTheirReportsRequest() throws Exception {
        cancel(managerToken, request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Only the requester or HR may cancel a leave request; a manager rejects it instead"));
    }

    @Test
    void anUnrelatedEmployeeCancellingGetsNotFound() throws Exception {
        cancel(unrelatedToken, request).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ state machine

    @Test
    void cancellingTwiceIsAConflict() throws Exception {
        cancel(staffToken, request).andExpect(status().isOk());
        cancel(staffToken, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This leave request is CANCELLED and can no longer become CANCELLED"));
    }

    @Test
    void cancellingARejectedRequestIsAConflict() throws Exception {
        reject(managerToken, request, "no").andExpect(status().isOk());
        cancel(staffToken, request).andExpect(status().isConflict());
    }

    @Test
    void cancellingAnUnknownRequestIsNotFound() throws Exception {
        cancel(staffToken, UUID.randomUUID()).andExpect(status().isNotFound());
    }
}
