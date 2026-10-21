package com.example.hr.leave;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Terminating an employee must leave no dangling reservations: their PENDING requests
 * become CANCELLED and the days they held are refunded, inside the same transaction as the
 * termination.
 */
class LeaveTerminationIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private String managerToken;
    private NewEmployee staff;
    private String staffToken;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        NewEmployee manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, null);
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());

        start = planningStart();
        end = endOf(start, 5);
    }

    private void terminate(UUID employeeId) throws Exception {
        mvc.perform(post("/api/employees/" + employeeId + "/terminate")
                .header("Authorization", bearer(admin))).andExpect(status().isOk());
    }

    private JsonNode balanceAsAdmin(UUID employeeId, LeaveType type, int year) throws Exception {
        JsonNode balances = body(getAs(admin, "/api/leave/balances/" + employeeId + "?year=" + year)
                .andExpect(status().isOk()));
        for (JsonNode node : balances) {
            if (type.name().equals(node.path("leaveType").asText())) {
                return node;
            }
        }
        throw new AssertionError("No " + type + " balance");
    }

    private JsonNode requestAsAdmin(UUID requestId) throws Exception {
        return body(getAs(admin, "/api/leave/requests/" + requestId).andExpect(status().isOk()));
    }

    @Test
    void terminationCancelsThePendingRequestsAndRefundsTheReservedDays() throws Exception {
        UUID pending = fileRequestOk(staffToken, LeaveType.ANNUAL, start, end);
        assertThat(balanceAsAdmin(staff.id(), LeaveType.ANNUAL, planningYear()).path("pendingDays").asInt())
                .isEqualTo(5);

        terminate(staff.id());

        assertThat(requestAsAdmin(pending).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(requestAsAdmin(pending).path("decisionNote").asText())
                .isEqualTo("Cancelled automatically: the employee was terminated");

        JsonNode balance = balanceAsAdmin(staff.id(), LeaveType.ANNUAL, planningYear());
        assertThat(balance.path("pendingDays").asInt()).isZero();
        assertThat(balance.path("usedDays").asInt()).isZero();
        assertThat(balance.path("remainingDays").asInt()).isEqualTo(21);
    }

    @Test
    void everyPendingRequestOfEveryTypeIsCancelled() throws Exception {
        UUID annual = fileRequestOk(staffToken, LeaveType.ANNUAL, start, end);
        LocalDate sickStart = nextWorkingDayAfter(end.plusDays(7));
        UUID sick = fileRequestOk(staffToken, LeaveType.SICK, sickStart, endOf(sickStart, 2));

        terminate(staff.id());

        assertThat(requestAsAdmin(annual).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(requestAsAdmin(sick).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(balanceAsAdmin(staff.id(), LeaveType.SICK, planningYear()).path("pendingDays").asInt())
                .isZero();
        assertThat(balanceAsAdmin(staff.id(), LeaveType.SICK, planningYear()).path("remainingDays").asInt())
                .isEqualTo(10);
    }

    @Test
    void anAlreadyApprovedRequestSurvivesTheTermination() throws Exception {
        UUID approved = fileRequestOk(staffToken, LeaveType.ANNUAL, start, end);
        approve(managerToken, approved).andExpect(status().isOk());

        terminate(staff.id());

        assertThat(requestAsAdmin(approved).path("status").asText()).isEqualTo("APPROVED");
        JsonNode balance = balanceAsAdmin(staff.id(), LeaveType.ANNUAL, planningYear());
        assertThat(balance.path("usedDays").asInt()).isEqualTo(5);
        assertThat(balance.path("pendingDays").asInt()).isZero();
    }

    @Test
    void aRejectedRequestIsNotTouchedAgain() throws Exception {
        UUID rejected = fileRequestOk(staffToken, LeaveType.ANNUAL, start, end);
        reject(managerToken, rejected, "no").andExpect(status().isOk());

        terminate(staff.id());

        JsonNode request = requestAsAdmin(rejected);
        assertThat(request.path("status").asText()).isEqualTo("REJECTED");
        assertThat(request.path("decisionNote").asText()).isEqualTo("no");
    }

    @Test
    void terminatingAnEmployeeWithoutAnyLeaveIsFine() throws Exception {
        terminate(staff.id());
        assertThat(balanceAsAdmin(staff.id(), LeaveType.ANNUAL, planningYear()).path("remainingDays").asInt())
                .isEqualTo(21);
    }

    @Test
    void theTerminatedEmployeeKeepsNoAccessToTheLeaveApi() throws Exception {
        fileRequestOk(staffToken, LeaveType.ANNUAL, start, end);
        terminate(staff.id());
        getAs(staffToken, "/api/leave/balances/me").andExpect(status().isUnauthorized());
    }
}
