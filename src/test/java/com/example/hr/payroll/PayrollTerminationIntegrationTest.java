package com.example.hr.payroll;

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
 * The termination date and what payroll does with it: {@code terminated_at} is written by
 * the existing terminate flow from the injected clock, a leaver's final month is pro-rated
 * to that date, and somebody who left in an earlier month is simply not in the run.
 */
class PayrollTerminationIntegrationTest extends AbstractPayrollIntegrationTest {

    private String adminToken;
    private String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        NewEmployee hr = createEmployee(adminToken, "Hana HR", Role.HR, null);
        hrToken = login(hr.email(), hr.password());
    }

    private void terminate(UUID employeeId) throws Exception {
        mvc.perform(post("/api/employees/" + employeeId + "/terminate")
                .header("Authorization", bearer(adminToken))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ terminated_at

    @Test
    void theTerminateFlowStoresTheTerminationDate() throws Exception {
        NewEmployee leaver = createEmployee(adminToken, "Leo Leaver", Role.EMPLOYEE, null);
        assertThat(jdbc.queryForObject("select terminated_at from employees where id = ?",
                LocalDate.class, leaver.id())).isNull();

        terminate(leaver.id());

        LocalDate stored = jdbc.queryForObject("select terminated_at from employees where id = ?",
                LocalDate.class, leaver.id());
        assertThat(stored).isEqualTo(LocalDate.now(ZONE));
        assertThat(jdbc.queryForObject("select status from employees where id = ?", String.class,
                leaver.id())).isEqualTo("TERMINATED");
    }

    @Test
    void theTerminationDateIsVisibleOnTheProfileToHr() throws Exception {
        NewEmployee leaver = createEmployee(adminToken, "Leo Leaver", Role.EMPLOYEE, null);
        terminate(leaver.id());

        JsonNode profile = body(getAs(hrToken, "/api/employees/" + leaver.id())
                .andExpect(status().isOk()));
        assertThat(profile.path("terminatedAt").asText()).isEqualTo(LocalDate.now(ZONE).toString());
    }

    @Test
    void anActiveEmployeeHasNoTerminationDateOnTheirPayslip() throws Exception {
        NewEmployee staff = createEmployee(adminToken, "Sara Staff", Role.EMPLOYEE, null, null,
                REFERENCE_SALARY);
        JsonNode slip = payslipOf(createRunOk(hrToken, MONTH_YEAR, MONTH), staff.id());

        assertThat(slip.path("terminatedAt").isNull()).isTrue();
        assertThat(slip.path("payableWorkingDays").asInt()).isEqualTo(WORKING_DAYS_IN_MARCH_2025);
    }

    // ------------------------------------------------------------------ proration

    /**
     * A leaver whose {@code terminated_at} is 2025-03-14 is payable on the working days
     * 3-03 … 3-13, i.e. 2,3,4,5,6,9,10,11,12,13 = 10 of 22 days.
     * prorated 8 800 × 10 / 22 = 4 000.00.
     */
    @Test
    void aMidMonthLeaverIsProratedToTheirTerminationDate() throws Exception {
        NewEmployee leaver = createEmployee(adminToken, "Leo Leaver", Role.EMPLOYEE, null, null,
                REFERENCE_SALARY);
        // The terminate flow always stamps "today", so a historic leaving date is set
        // directly — exactly what an HR data migration would do.
        terminate(leaver.id());
        jdbc.update("update employees set terminated_at = ? where id = ?", march(14), leaver.id());

        JsonNode slip = payslipOf(createRunOk(hrToken, MONTH_YEAR, MONTH), leaver.id());
        assertThat(slip.path("terminatedAt").asText()).isEqualTo("2025-03-14");
        assertThat(slip.path("payableWorkingDays").asInt()).isEqualTo(10);
        assertThat(slip.path("proratedBase").decimalValue()).isEqualByComparingTo("4000.00");
        // Only the ten payable days can be absent: the days after leaving are not counted.
        assertThat(slip.path("absentDays").asInt()).isEqualTo(10);
        assertThat(slip.path("absenceDeduction").decimalValue()).isEqualByComparingTo("4000.00");
    }

    @Test
    void aLeaverWhoWorkedTheirWholeNoticeIsPaidForThoseDaysOnly() throws Exception {
        NewEmployee leaver = createEmployee(adminToken, "Leo Leaver", Role.EMPLOYEE, null, null,
                REFERENCE_SALARY);
        terminate(leaver.id());
        jdbc.update("update employees set terminated_at = ? where id = ?", march(14), leaver.id());

        for (int dayOfMonth : java.util.List.of(2, 3, 4, 5, 6, 9, 10, 11, 12, 13)) {
            addSessionOk(hrToken, leaver.id(), march(dayOfMonth), "09:00", "17:00");
        }

        JsonNode slip = payslipOf(createRunOk(hrToken, MONTH_YEAR, MONTH), leaver.id());
        assertThat(slip.path("absentDays").asInt()).isZero();
        assertThat(slip.path("proratedBase").decimalValue()).isEqualByComparingTo("4000.00");
        assertThat(slip.path("grossEarnings").decimalValue()).isEqualByComparingTo("4000.00");
        // insurance 8 800 × 0.11 = 968.00; taxable 4 000 − 968 − 1 250 = 1 782.00;
        // tax 282 × 0.10 = 28.20; net 4 000 − 968 − 28.20 = 3 003.80
        assertThat(slip.path("insurance").decimalValue()).isEqualByComparingTo("968.00");
        assertThat(slip.path("taxableIncome").decimalValue()).isEqualByComparingTo("1782.00");
        assertThat(slip.path("tax").decimalValue()).isEqualByComparingTo("28.20");
        assertThat(slip.path("netPay").decimalValue()).isEqualByComparingTo("3003.80");
    }

    @Test
    void somebodyTerminatedInAnEarlierMonthIsNotInTheRunAtAll() throws Exception {
        NewEmployee leaver = createEmployee(adminToken, "Leo Leaver", Role.EMPLOYEE, null, null,
                REFERENCE_SALARY);
        terminate(leaver.id());
        jdbc.update("update employees set terminated_at = ? where id = ?",
                LocalDate.of(2025, 2, 20), leaver.id());

        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);
        assertThat(run.path("payslips")).noneMatch(
                slip -> leaver.id().toString().equals(slip.path("employeeId").asText()));
    }

    @Test
    void somebodyTerminatedWithoutADateIsTreatedAsHistoricAndSkipped() throws Exception {
        NewEmployee leaver = createEmployee(adminToken, "Leo Legacy", Role.EMPLOYEE, null, null,
                REFERENCE_SALARY);
        terminate(leaver.id());
        // A row that predates the V4 migration has no termination date.
        jdbc.update("update employees set terminated_at = null where id = ?", leaver.id());

        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);
        assertThat(run.path("payslips")).noneMatch(
                slip -> leaver.id().toString().equals(slip.path("employeeId").asText()));
    }

    @Test
    void aLeaversFinalizedPayslipStaysReadableToThemAfterTheyLeave() throws Exception {
        NewEmployee leaver = createEmployee(adminToken, "Leo Leaver", Role.EMPLOYEE, null, null,
                REFERENCE_SALARY);
        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);
        UUID runId = runIdOf(run);
        UUID payslipId = payslipIdOf(run, leaver.id());
        finalizeRun(hrToken, runId).andExpect(status().isOk());

        terminate(leaver.id());

        // HR can still read it; the leaver's own token is rejected by the security filter,
        // which is the Phase 1 rule and is not weakened here.
        payslip(hrToken, payslipId).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from payslips where employee_id = ?",
                Long.class, leaver.id())).isEqualTo(1L);
    }
}
