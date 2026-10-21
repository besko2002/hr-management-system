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
 * The team calendar: scope (own subtree, everything for HR/ADMIN), only APPROVED leave,
 * and never a word about why somebody is off.
 */
class LeaveCalendarIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private NewEmployee staff;
    private String ceoToken;
    private String managerToken;
    private String staffToken;
    private String unrelatedToken;
    private String hrToken;

    private LocalDate start;
    private LocalDate end;
    private UUID approvedRequest;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        NewEmployee ceo = createEmployee(admin, "Cora Ceo", Role.EMPLOYEE, null);
        NewEmployee manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, ceo.id());
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        NewEmployee unrelated = createEmployee(admin, "Ulla Unrelated", Role.EMPLOYEE, null);
        NewEmployee hr = createEmployee(admin, "Hilda Hr", Role.HR, null);

        ceoToken = login(ceo.email(), ceo.password());
        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());
        unrelatedToken = login(unrelated.email(), unrelated.password());
        hrToken = login(hr.email(), hr.password());

        start = planningStart();
        end = endOf(start, 5);
        approvedRequest = UUID.fromString(
                body(fileRequest(staffToken, LeaveType.ANNUAL, start, end, "Family holiday")
                        .andExpect(status().isCreated())).path("id").asText());
        approve(managerToken, approvedRequest).andExpect(status().isOk());
    }

    private JsonNode calendar(String token, LocalDate from, LocalDate to) throws Exception {
        return body(getAs(token, "/api/leave/calendar?from=" + from + "&to=" + to).andExpect(status().isOk()));
    }

    private JsonNode wholeWindow(String token) throws Exception {
        return calendar(token, start.minusDays(1), end.plusDays(1));
    }

    @Test
    void theDirectManagerSeesTheApprovedLeaveOfTheirReport() throws Exception {
        JsonNode entries = wholeWindow(managerToken);
        assertThat(entries).hasSize(1);
        JsonNode entry = entries.get(0);
        assertThat(entry.path("requestId").asText()).isEqualTo(approvedRequest.toString());
        assertThat(entry.path("employeeId").asText()).isEqualTo(staff.id().toString());
        assertThat(entry.path("employeeName").asText()).isEqualTo("Stan Staff");
        assertThat(entry.path("leaveType").asText()).isEqualTo("ANNUAL");
        assertThat(entry.path("startDate").asText()).isEqualTo(start.toString());
        assertThat(entry.path("endDate").asText()).isEqualTo(end.toString());
        assertThat(entry.path("workingDays").asInt()).isEqualTo(5);
    }

    @Test
    void theCalendarNeverCarriesTheReason() throws Exception {
        UUID withReason = fileRequestOk(staffToken, LeaveType.ANNUAL,
                nextWorkingDayAfter(end.plusDays(7)), nextWorkingDayAfter(end.plusDays(7)));
        approve(managerToken, withReason).andExpect(status().isOk());

        for (JsonNode entry : calendar(managerToken, start, end.plusDays(30))) {
            assertThat(entry.has("reason")).as("calendar entries must not expose a reason").isFalse();
            assertThat(entry.toString()).doesNotContain("reason");
        }
        // The same request does carry its reason on the detail endpoint, for those allowed to see it.
        getAs(managerToken, "/api/leave/requests/" + approvedRequest).andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").exists());
    }

    @Test
    void anIndirectManagerSeesTheirWholeSubtree() throws Exception {
        assertThat(wholeWindow(ceoToken)).hasSize(1);
    }

    @Test
    void theEmployeeSeesTheirOwnApprovedLeave() throws Exception {
        assertThat(wholeWindow(staffToken)).hasSize(1);
    }

    @Test
    void anUnrelatedEmployeeSeesNothing() throws Exception {
        assertThat(wholeWindow(unrelatedToken)).isEmpty();
    }

    @Test
    void hrAndAdminSeeTheWholeCompany() throws Exception {
        assertThat(wholeWindow(hrToken)).hasSize(1);
        assertThat(wholeWindow(admin)).hasSize(1);
    }

    @Test
    void pendingLeaveIsNotOnTheCalendar() throws Exception {
        LocalDate later = nextWorkingDayAfter(end.plusDays(7));
        fileRequestOk(staffToken, LeaveType.ANNUAL, later, later);
        JsonNode entries = calendar(managerToken, later, later);
        assertThat(entries).isEmpty();
    }

    @Test
    void rejectedAndCancelledLeaveIsNotOnTheCalendar() throws Exception {
        cancel(staffToken, approvedRequest).andExpect(status().isOk());
        assertThat(wholeWindow(managerToken)).isEmpty();
    }

    @Test
    void leaveOutsideTheRangeIsNotReturned() throws Exception {
        assertThat(calendar(managerToken, end.plusDays(1), end.plusDays(20))).isEmpty();
        assertThat(calendar(managerToken, start.minusDays(20), start.minusDays(1))).isEmpty();
    }

    @Test
    void aRangeTouchingOnlyOneDayOfTheLeaveStillReturnsIt() throws Exception {
        assertThat(calendar(managerToken, end, end)).hasSize(1);
        assertThat(calendar(managerToken, start, start)).hasSize(1);
    }

    @Test
    void anInvertedRangeIsRejected() throws Exception {
        getAs(managerToken, "/api/leave/calendar?from=" + end + "&to=" + start)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'to' must not be before 'from'"));
    }

    @Test
    void anExcessivelyLongRangeIsRejected() throws Exception {
        getAs(managerToken, "/api/leave/calendar?from=" + start + "&to=" + start.plusYears(2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The calendar range must not exceed 366 days"));
    }

    @Test
    void theRangeBoundsAreRequired() throws Exception {
        getAs(managerToken, "/api/leave/calendar").andExpect(status().isBadRequest());
        getAs(managerToken, "/api/leave/calendar?from=" + start).andExpect(status().isBadRequest());
    }
}
