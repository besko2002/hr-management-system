package com.example.hr.leave;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lazy balance creation, the pro-rated entitlement of a new joiner and who may read whose
 * balances.
 */
class LeaveBalanceIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private NewEmployee ceo;
    private NewEmployee manager;
    private NewEmployee staff;
    private String staffToken;
    private String managerToken;
    private String ceoToken;
    private String hrToken;
    private String unrelatedToken;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        ceo = createEmployee(admin, "Cora Ceo", Role.EMPLOYEE, null);
        manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, ceo.id());
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        NewEmployee hr = createEmployee(admin, "Hilda Hr", Role.HR, null);
        NewEmployee unrelated = createEmployee(admin, "Ulla Unrelated", Role.EMPLOYEE, null);

        staffToken = login(staff.email(), staff.password());
        managerToken = login(manager.email(), manager.password());
        ceoToken = login(ceo.email(), ceo.password());
        hrToken = login(hr.email(), hr.password());
        unrelatedToken = login(unrelated.email(), unrelated.password());
    }

    // ------------------------------------------------------------------ lazy creation

    @Test
    void theBalancesOfAnEstablishedEmployeeHoldTheFullYearlyAllowance() throws Exception {
        JsonNode balances = balances(staffToken, null);
        assertThat(balances).hasSize(3);

        JsonNode annual = balance(staffToken, LeaveType.ANNUAL, null);
        assertThat(annual.path("entitledDays").asInt()).isEqualTo(21);
        assertThat(annual.path("carriedOverDays").asInt()).isZero();
        assertThat(annual.path("usedDays").asInt()).isZero();
        assertThat(annual.path("pendingDays").asInt()).isZero();
        assertThat(annual.path("remainingDays").asInt()).isEqualTo(21);
        assertThat(annual.path("paid").asBoolean()).isTrue();
        assertThat(annual.path("requiresBalance").asBoolean()).isTrue();

        JsonNode sick = balance(staffToken, LeaveType.SICK, null);
        assertThat(sick.path("entitledDays").asInt()).isEqualTo(10);
        assertThat(sick.path("remainingDays").asInt()).isEqualTo(10);
    }

    @Test
    void unpaidLeaveHasNoAllowanceAndNoRemaining() throws Exception {
        JsonNode unpaid = balance(staffToken, LeaveType.UNPAID, null);
        assertThat(unpaid.path("entitledDays").asInt()).isZero();
        assertThat(unpaid.path("paid").asBoolean()).isFalse();
        assertThat(unpaid.path("requiresBalance").asBoolean()).isFalse();
        assertThat(unpaid.path("remainingDays").isNull()).isTrue();
    }

    @Test
    void theBalanceYearDefaultsToTheCurrentYear() throws Exception {
        assertThat(balance(staffToken, LeaveType.ANNUAL, null).path("year").asInt())
                .isEqualTo(LocalDate.now().getYear());
    }

    @Test
    void everyYearHasItsOwnIndependentBalance() throws Exception {
        int thisYear = LocalDate.now().getYear();
        fileDaysOk(staffToken, LeaveType.ANNUAL, planningStart(), 4);

        int pendingInPlanningYear = balance(staffToken, LeaveType.ANNUAL, planningYear())
                .path("pendingDays").asInt();
        int pendingTwoYearsLater = balance(staffToken, LeaveType.ANNUAL, thisYear + 2)
                .path("pendingDays").asInt();

        assertThat(pendingInPlanningYear).isEqualTo(4);
        assertThat(pendingTwoYearsLater).isZero();
        assertThat(balance(staffToken, LeaveType.ANNUAL, thisYear + 2).path("entitledDays").asInt())
                .isEqualTo(21);
    }

    // ------------------------------------------------------------------ pro-rating

    @Test
    void aNewJoinerGetsAProRatedEntitlementForTheirHireYear() throws Exception {
        int year = LocalDate.now().getYear();
        NewEmployee july = createEmployee(admin, "Jules July", Role.EMPLOYEE, manager.id(), null, "3000.00",
                LocalDate.of(year, 7, 15).toString());
        String token = login(july.email(), july.password());

        // round(21 × (13 − 7) / 12) = round(10.5) = 11, round(10 × 6 / 12) = 5
        assertThat(balance(token, LeaveType.ANNUAL, year).path("entitledDays").asInt()).isEqualTo(11);
        assertThat(balance(token, LeaveType.SICK, year).path("entitledDays").asInt()).isEqualTo(5);
    }

    @Test
    void aDecemberJoinerGetsTwoAnnualDays() throws Exception {
        int year = LocalDate.now().getYear();
        NewEmployee december = createEmployee(admin, "Dana December", Role.EMPLOYEE, manager.id(), null,
                "3000.00", LocalDate.of(year, 12, 1).toString());
        String token = login(december.email(), december.password());
        assertThat(balance(token, LeaveType.ANNUAL, year).path("entitledDays").asInt()).isEqualTo(2);
    }

    @Test
    void aJanuaryJoinerGetsTheWholeAllowance() throws Exception {
        int year = LocalDate.now().getYear();
        NewEmployee january = createEmployee(admin, "Jan January", Role.EMPLOYEE, manager.id(), null,
                "3000.00", LocalDate.of(year, 1, 20).toString());
        String token = login(january.email(), january.password());
        assertThat(balance(token, LeaveType.ANNUAL, year).path("entitledDays").asInt()).isEqualTo(21);
    }

    @Test
    void theYearAfterTheHireYearIsNoLongerProRated() throws Exception {
        int year = LocalDate.now().getYear();
        NewEmployee july = createEmployee(admin, "Jules July", Role.EMPLOYEE, manager.id(), null, "3000.00",
                LocalDate.of(year, 7, 15).toString());
        String token = login(july.email(), july.password());
        assertThat(balance(token, LeaveType.ANNUAL, year + 1).path("entitledDays").asInt()).isEqualTo(21);
    }

    @Test
    void theYearBeforeTheHireYearHasNoEntitlement() throws Exception {
        int year = LocalDate.now().getYear();
        NewEmployee july = createEmployee(admin, "Jules July", Role.EMPLOYEE, manager.id(), null, "3000.00",
                LocalDate.of(year, 7, 15).toString());
        String token = login(july.email(), july.password());
        assertThat(balance(token, LeaveType.ANNUAL, year - 1).path("entitledDays").asInt()).isZero();
    }

    // ------------------------------------------------------------------ access

    @Test
    void anEmployeeCanReadTheirOwnBalancesByIdAsWell() throws Exception {
        getAs(staffToken, "/api/leave/balances/" + staff.id()).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveType").value("ANNUAL"));
    }

    @Test
    void theDirectManagerAndHigherAncestorsCanReadABalance() throws Exception {
        getAs(managerToken, "/api/leave/balances/" + staff.id()).andExpect(status().isOk());
        getAs(ceoToken, "/api/leave/balances/" + staff.id()).andExpect(status().isOk());
    }

    @Test
    void hrAndAdminCanReadAnyBalance() throws Exception {
        getAs(hrToken, "/api/leave/balances/" + staff.id()).andExpect(status().isOk());
        getAs(admin, "/api/leave/balances/" + staff.id()).andExpect(status().isOk());
    }

    @Test
    void anUnrelatedEmployeeGetsNotFound() throws Exception {
        getAs(unrelatedToken, "/api/leave/balances/" + staff.id()).andExpect(status().isNotFound());
    }

    @Test
    void aSubordinateCannotReadTheirManagersBalance() throws Exception {
        getAs(staffToken, "/api/leave/balances/" + manager.id()).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownEmployeeIdIsNotFound() throws Exception {
        getAs(admin, "/api/leave/balances/" + java.util.UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void anImpossibleYearIsRejected() throws Exception {
        getAs(staffToken, "/api/leave/balances/me?year=1900").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("year must be between 2000 and 2100"));
        getAs(staffToken, "/api/leave/balances/me?year=3000").andExpect(status().isBadRequest());
    }

    @Test
    void anUnauthenticatedCallerSeesNoBalances() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/leave/balances/me"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ leave types

    @Test
    void hrAndAdminCanReadTheLeaveTypeConfiguration() throws Exception {
        JsonNode types = body(getAs(admin, "/api/leave/types").andExpect(status().isOk()));
        assertThat(types).hasSize(3);
        assertThat(types.get(0).path("code").asText()).isEqualTo("ANNUAL");
        assertThat(types.get(0).path("annualAllowanceDays").asInt()).isEqualTo(21);
        assertThat(types.get(1).path("code").asText()).isEqualTo("SICK");
        assertThat(types.get(1).path("annualAllowanceDays").asInt()).isEqualTo(10);
        assertThat(types.get(2).path("code").asText()).isEqualTo("UNPAID");
        assertThat(types.get(2).path("requiresBalance").asBoolean()).isFalse();
        getAs(hrToken, "/api/leave/types").andExpect(status().isOk());
    }

    @Test
    void aPlainEmployeeCannotReadTheLeaveTypeConfiguration() throws Exception {
        getAs(staffToken, "/api/leave/types").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Only HR or an ADMIN may read the leave-type configuration"));
    }
}
