package com.example.hr.leave;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The monthly accrual in its own mode: {@code app.leave.annual.upfront=false}, so the
 * ANNUAL entitlement starts at 0 and is built up month by month. It credits whole days, it
 * is idempotent per (employee, year, month) and it only touches ACTIVE employees who were
 * already hired.
 *
 * <p>This is the only test class that needs a second Spring context; everything else runs
 * in the default (upfront) configuration.
 */
@TestPropertySource(properties = "app.leave.annual.upfront=false")
class LeaveAccrualMonthlyIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private NewEmployee staff;
    private String staffToken;
    private String hrToken;
    private int year;
    private int month;
    private int expectedCredit;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        NewEmployee manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, null);
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        NewEmployee hr = createEmployee(admin, "Hilda Hr", Role.HR, null);
        staffToken = login(staff.email(), staff.password());
        hrToken = login(hr.email(), hr.password());

        year = LocalDate.now().getYear();
        month = LocalDate.now().getMonthValue();
        expectedCredit = LeaveAccrualService.monthlyCredit(21, month);
    }

    private long accrualRows(UUID employeeId) {
        Long count = jdbc.queryForObject("select count(*) from accrual_log where employee_id = ?",
                Long.class, employeeId);
        return count == null ? 0 : count;
    }

    private int entitled(int forYear) throws Exception {
        return balance(staffToken, LeaveType.ANNUAL, forYear).path("entitledDays").asInt();
    }

    // ------------------------------------------------------------------ starting point

    @Test
    void inAccrualModeTheAnnualEntitlementStartsAtZero() throws Exception {
        JsonNode annual = balance(staffToken, LeaveType.ANNUAL, year);
        assertThat(annual.path("entitledDays").asInt()).isZero();
        assertThat(annual.path("remainingDays").asInt()).isZero();
        // SICK is not accrued, so it keeps its upfront allowance.
        assertThat(balance(staffToken, LeaveType.SICK, year).path("entitledDays").asInt()).isEqualTo(10);
    }

    @Test
    void withoutAnyAccrualThereIsNothingToSpend() throws Exception {
        fileRequest(staffToken, LeaveType.ANNUAL, planningStart(), planningStart())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Not enough ANNUAL balance")));
    }

    // ------------------------------------------------------------------ crediting

    @Test
    void theAccrualCreditsEveryActiveEmployee() throws Exception {
        JsonNode result = body(runAccrual(admin, year, month).andExpect(status().isOk()));
        assertThat(result.path("leaveType").asText()).isEqualTo("ANNUAL");
        assertThat(result.path("year").asInt()).isEqualTo(year);
        assertThat(result.path("month").asInt()).isEqualTo(month);
        assertThat(result.path("daysPerEmployee").asInt()).isEqualTo(expectedCredit);
        assertThat(result.path("employeesConsidered").asInt()).isEqualTo(4);
        assertThat(result.path("employeesCredited").asInt()).isEqualTo(4);
        assertThat(result.path("employeesAlreadyCredited").asInt()).isZero();

        assertThat(entitled(year)).isEqualTo(expectedCredit);
    }

    @Test
    void runningTheSameMonthAgainAddsNothing() throws Exception {
        runAccrual(admin, year, month).andExpect(status().isOk());
        int afterFirstRun = entitled(year);

        JsonNode second = body(runAccrual(admin, year, month).andExpect(status().isOk()));
        assertThat(second.path("employeesCredited").asInt()).isZero();
        assertThat(second.path("employeesAlreadyCredited").asInt()).isEqualTo(4);

        assertThat(entitled(year)).isEqualTo(afterFirstRun);
        assertThat(accrualRows(staff.id())).isEqualTo(1);
    }

    @Test
    void runningItAThirdTimeStillAddsNothing() throws Exception {
        runAccrual(admin, year, month).andExpect(status().isOk());
        runAccrual(admin, year, month).andExpect(status().isOk());
        JsonNode third = body(runAccrual(admin, year, month).andExpect(status().isOk()));
        assertThat(third.path("employeesCredited").asInt()).isZero();
        assertThat(accrualRows(staff.id())).isEqualTo(1);
    }

    @Test
    void aDifferentMonthCreditsAgain() throws Exception {
        runAccrual(admin, year, 1).andExpect(status().isOk());
        runAccrual(admin, year, 2).andExpect(status().isOk());

        assertThat(entitled(year)).isEqualTo(
                LeaveAccrualService.monthlyCredit(21, 1) + LeaveAccrualService.monthlyCredit(21, 2));
        assertThat(accrualRows(staff.id())).isEqualTo(2);
    }

    @Test
    void twelveMonthlyRunsReachExactlyTheYearlyAllowanceWithoutDrift() throws Exception {
        assertThat(entitled(year)).isZero();
        for (int m = 1; m <= 12; m++) {
            runAccrual(admin, year, m).andExpect(status().isOk());
        }
        assertThat(entitled(year)).isEqualTo(21);
        assertThat(accrualRows(staff.id())).isEqualTo(12);

        // And a thirteenth pass over the whole year changes nothing.
        for (int m = 1; m <= 12; m++) {
            runAccrual(admin, year, m).andExpect(status().isOk())
                    .andExpect(jsonPath("$.employeesCredited").value(0));
        }
        assertThat(entitled(year)).isEqualTo(21);
        assertThat(accrualRows(staff.id())).isEqualTo(12);
    }

    @Test
    void aDifferentYearIsAccountedSeparately() throws Exception {
        runAccrual(admin, year, month).andExpect(status().isOk());
        runAccrual(admin, year + 1, month).andExpect(status().isOk());

        assertThat(entitled(year)).isEqualTo(expectedCredit);
        assertThat(entitled(year + 1)).isEqualTo(expectedCredit);
        assertThat(accrualRows(staff.id())).isEqualTo(2);
    }

    @Test
    void theRunDefaultsToTheCurrentMonth() throws Exception {
        mvc.perform(post("/api/leave/accrual/run").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(year))
                .andExpect(jsonPath("$.month").value(month));
    }

    @Test
    void theAccruedDaysAreAvailableForNewRequests() throws Exception {
        int planningMonth = planningStart().getMonthValue();
        runAccrual(admin, planningYear(), planningMonth).andExpect(status().isOk());
        int credit = LeaveAccrualService.monthlyCredit(21, planningMonth);

        assertThat(remaining(staffToken, LeaveType.ANNUAL, planningYear())).isEqualTo(credit);
        fileRequest(staffToken, LeaveType.ANNUAL, planningStart(), planningStart())
                .andExpect(status().isCreated());
        assertThat(remaining(staffToken, LeaveType.ANNUAL, planningYear())).isEqualTo(credit - 1);
    }

    // ------------------------------------------------------------------ who is credited

    @Test
    void aTerminatedEmployeeIsNotCredited() throws Exception {
        mvc.perform(post("/api/employees/" + staff.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        JsonNode result = body(runAccrual(admin, year, month).andExpect(status().isOk()));
        assertThat(result.path("employeesConsidered").asInt()).isEqualTo(3);
        assertThat(accrualRows(staff.id())).isZero();
        assertThat(body(getAs(admin, "/api/leave/balances/" + staff.id() + "?year=" + year))
                .get(0).path("entitledDays").asInt()).isZero();
    }

    @Test
    void anEmployeeHiredAfterTheAccrualMonthIsNotCredited() throws Exception {
        LocalDate laterHireDate = LocalDate.now().plusMonths(3).withDayOfMonth(1);
        NewEmployee future = createEmployee(admin, "Fiona Future", Role.EMPLOYEE, null, null, "3000.00",
                laterHireDate.toString());

        runAccrual(admin, year, month).andExpect(status().isOk());
        assertThat(accrualRows(future.id())).isZero();
        assertThat(accrualRows(staff.id())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ authority and validation

    @Test
    void onlyHrOrAdminMayRunTheAccrual() throws Exception {
        runAccrual(staffToken, year, month).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only HR or an ADMIN may run the leave accrual"));
        runAccrual(hrToken, year, month).andExpect(status().isOk());
    }

    @Test
    void anImpossibleMonthIsRejected() throws Exception {
        runAccrual(admin, year, 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("month must be between 1 and 12"));
        runAccrual(admin, year, 13).andExpect(status().isBadRequest());
    }

    @Test
    void anImpossibleYearIsRejected() throws Exception {
        runAccrual(admin, 1999, month).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("year must be between 2000 and 2100"));
    }
}
