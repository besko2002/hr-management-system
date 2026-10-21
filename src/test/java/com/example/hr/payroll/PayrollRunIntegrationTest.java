package com.example.hr.payroll;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The monthly run end to end, against the reference March 2025 scenario.
 *
 * <h2>The hand arithmetic every assertion below is checked against</h2>
 * <pre>
 *   base                   8 800.00   (monthly gross)
 *   working days in month        22   (March 2025, Fri+Sat weekend, no holidays)
 *   payable working days         22   (employed all month)
 *   dailyRate      8 800 / 22 = 400.00
 *   hourlyRate       400 / 8  =  50.00
 *   overtime       180 min = 3.00 h x 50.00 x 1.5 =   225.00
 *   holiday work   240 min = 4.00 h x 50.00 x 2.0 =   400.00
 *   unpaid leave   1 day   x 400.00              = − 400.00
 *   absence        1 day   x 400.00              = − 400.00
 *   gross          8 800 + 225 + 400 − 400 − 400 = 8 625.00
 *   insurance      clamp(8 800, 2 000, 12 600) x 0.11 = 968.00
 *   taxable        8 625 − 968 − 1 250           = 6 407.00
 *   tax            0 + 150 + 300 + 1 407 x 0.20  =   731.40
 *   net            8 625 − 968 − 731.40          = 6 925.60
 *   late           2 days x 30 min               =    60 min (reported, not deducted)
 * </pre>
 */
class PayrollRunIntegrationTest extends AbstractPayrollIntegrationTest {

    private String adminToken;
    private String hrToken;
    private NewEmployee staff;
    private String staffToken;
    private NewEmployee manager;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        NewEmployee hr = createEmployee(adminToken, "Hana HR", Role.HR, null);
        hrToken = login(hr.email(), hr.password());
        manager = createEmployee(adminToken, "Mary Manager", Role.EMPLOYEE, null);
        staff = createEmployee(adminToken, "Sara Staff", Role.EMPLOYEE, manager.id(), null,
                REFERENCE_SALARY);
        staffToken = login(staff.email(), staff.password());
    }

    // ------------------------------------------------------------------ the figures

    @Test
    void theReferenceMonthProducesExactlyTheHandComputedPayslip() throws Exception {
        buildReferenceMarch(hrToken, staff.id());

        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);
        JsonNode slip = payslipOf(run, staff.id());

        assertThat(slip.path("baseSalary").decimalValue()).isEqualByComparingTo("8800.00");
        assertThat(slip.path("workingDaysInMonth").asInt()).isEqualTo(WORKING_DAYS_IN_MARCH_2025);
        assertThat(slip.path("payableWorkingDays").asInt()).isEqualTo(22);
        assertThat(slip.path("dailyRate").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(slip.path("hourlyRate").decimalValue()).isEqualByComparingTo("50.00");

        assertThat(slip.path("lateMinutes").asInt()).isEqualTo(60);
        assertThat(slip.path("overtimeMinutes").asInt()).isEqualTo(180);
        assertThat(slip.path("overtimeHours").decimalValue()).isEqualByComparingTo("3.00");
        assertThat(slip.path("overtimePay").decimalValue()).isEqualByComparingTo("225.00");
        assertThat(slip.path("holidayMinutes").asInt()).isEqualTo(240);
        assertThat(slip.path("holidayHours").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(slip.path("holidayPay").decimalValue()).isEqualByComparingTo("400.00");

        assertThat(slip.path("unpaidLeaveDays").asInt()).isEqualTo(1);
        assertThat(slip.path("unpaidLeaveDeduction").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(slip.path("absentDays").asInt()).isEqualTo(1);
        assertThat(slip.path("absenceDeduction").decimalValue()).isEqualByComparingTo("400.00");

        assertThat(slip.path("proratedBase").decimalValue()).isEqualByComparingTo("8800.00");
        assertThat(slip.path("grossEarnings").decimalValue()).isEqualByComparingTo("8625.00");
        assertThat(slip.path("insurableWage").decimalValue()).isEqualByComparingTo("8800.00");
        assertThat(slip.path("insurance").decimalValue()).isEqualByComparingTo("968.00");
        assertThat(slip.path("personalExemption").decimalValue()).isEqualByComparingTo("1250.00");
        assertThat(slip.path("taxableIncome").decimalValue()).isEqualByComparingTo("6407.00");
        assertThat(slip.path("tax").decimalValue()).isEqualByComparingTo("731.40");
        assertThat(slip.path("netPay").decimalValue()).isEqualByComparingTo("6925.60");
        assertThat(slip.path("netFloored").asBoolean()).isFalse();
        assertThat(slip.path("warning").isNull()).isTrue();
    }

    @Test
    void thePayslipCarriesTheMarginalTaxBreakdownThatAddsUpToTheTaxLine() throws Exception {
        buildReferenceMarch(hrToken, staff.id());
        JsonNode slip = payslipOf(createRunOk(hrToken, MONTH_YEAR, MONTH), staff.id());

        JsonNode breakdown = slip.path("taxBreakdown");
        assertThat(breakdown).hasSize(4);
        assertThat(breakdown.get(0).path("taxedAmount").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(breakdown.get(0).path("tax").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(breakdown.get(1).path("tax").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(breakdown.get(2).path("tax").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(breakdown.get(3).path("taxedAmount").decimalValue()).isEqualByComparingTo("1407.00");
        assertThat(breakdown.get(3).path("tax").decimalValue()).isEqualByComparingTo("281.40");

        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode charge : breakdown) {
            sum = sum.add(charge.path("tax").decimalValue());
        }
        assertThat(sum).isEqualByComparingTo(slip.path("tax").decimalValue());
    }

    @Test
    void thePayslipAlsoCarriesTheRatesItWasComputedWith() throws Exception {
        JsonNode slip = payslipOf(createRunOk(hrToken, MONTH_YEAR, MONTH), staff.id());
        JsonNode rates = slip.path("ratesUsed");

        assertThat(rates.path("workingHoursPerDay").asInt()).isEqualTo(8);
        assertThat(rates.path("overtimeMultiplier").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(rates.path("holidayOvertimeMultiplier").decimalValue()).isEqualByComparingTo("2.0");
        assertThat(rates.path("insuranceEmployeeRate").decimalValue()).isEqualByComparingTo("0.11");
        assertThat(rates.path("minInsurableWage").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(rates.path("maxInsurableWage").decimalValue()).isEqualByComparingTo("12600.00");
        assertThat(rates.path("personalExemptionMonthly").decimalValue()).isEqualByComparingTo("1250.00");
        assertThat(rates.path("taxBrackets")).hasSize(6);
        assertThat(rates.path("taxBrackets").get(5).path("upTo").isNull()).isTrue();
    }

    @Test
    void anEmployeeWithNoAttendanceAtAllIsFullyAbsentAndTheNetFloorsAtZero() throws Exception {
        // The manager (salary 5000, 22 working days -> daily 227.27) worked no day at all:
        // 22 x 227.27 = 5 000.00 (rounded 227.27 x 22 = 4 999.94 -> deduction 4 999.94),
        // gross 5 000.00 - 4 999.94 = 0.06, insurance 550.00 -> net -549.94 -> floored.
        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);
        JsonNode slip = payslipOf(run, manager.id());

        assertThat(slip.path("absentDays").asInt()).isEqualTo(22);
        assertThat(slip.path("dailyRate").decimalValue()).isEqualByComparingTo("227.27");
        assertThat(slip.path("absenceDeduction").decimalValue()).isEqualByComparingTo("4999.94");
        assertThat(slip.path("grossEarnings").decimalValue()).isEqualByComparingTo("0.06");
        assertThat(slip.path("insurance").decimalValue()).isEqualByComparingTo("550.00");
        assertThat(slip.path("netPay").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(slip.path("netFloored").asBoolean()).isTrue();
        assertThat(slip.path("warning").asText()).contains("floored");
    }

    @Test
    void theRunTotalsAreTheSumOfItsPayslips() throws Exception {
        buildReferenceMarch(hrToken, staff.id());
        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal insurance = BigDecimal.ZERO;
        for (JsonNode slip : run.path("payslips")) {
            gross = gross.add(slip.path("grossEarnings").decimalValue());
            net = net.add(slip.path("netPay").decimalValue());
            insurance = insurance.add(slip.path("insurance").decimalValue());
        }
        JsonNode totals = run.path("totals");
        assertThat(totals.path("employees").asInt()).isEqualTo(run.path("payslips").size());
        assertThat(totals.path("grossEarnings").decimalValue()).isEqualByComparingTo(gross);
        assertThat(totals.path("netPay").decimalValue()).isEqualByComparingTo(net);
        assertThat(totals.path("insurance").decimalValue()).isEqualByComparingTo(insurance);
    }

    // ------------------------------------------------------------------ eligibility

    @Test
    void onePayslipIsCreatedPerEligibleEmployeeAndNoneForAnyoneWithoutASalary() throws Exception {
        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);

        // HR, the manager and the staff member all have a salary; the bootstrap admin does not.
        assertThat(run.path("payslips")).hasSize(3);
        assertThat(run.path("run").path("payslipCount").asInt()).isEqualTo(3);
        Long adminSlips = jdbc.queryForObject("""
                select count(*) from payslips p join employees e on e.id = p.employee_id
                 where e.salary is null
                """, Long.class);
        assertThat(adminSlips).isZero();
    }

    @Test
    void somebodyHiredAfterTheMonthEndGetsNoPayslip() throws Exception {
        NewEmployee future = createEmployee(adminToken, "Fady Future", Role.EMPLOYEE, null, null,
                "5000.00", "2025-04-01");

        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);
        assertThat(run.path("payslips")).noneMatch(
                slip -> future.id().toString().equals(slip.path("employeeId").asText()));
    }

    @Test
    void aMidMonthJoinerIsProratedByPayableWorkingDays() throws Exception {
        // Hired 2025-03-17: working days 17,18,19,20,23,24,25,26,27,30,31 = 11 of 22.
        // prorated 8 800 x 11 / 22 = 4 400.00 and 11 absences x 400.00 = 4 400.00.
        NewEmployee joiner = createEmployee(adminToken, "Jana Joiner", Role.EMPLOYEE, null, null,
                REFERENCE_SALARY, "2025-03-17");

        JsonNode slip = payslipOf(createRunOk(hrToken, MONTH_YEAR, MONTH), joiner.id());
        assertThat(slip.path("payableWorkingDays").asInt()).isEqualTo(11);
        assertThat(slip.path("proratedBase").decimalValue()).isEqualByComparingTo("4400.00");
        assertThat(slip.path("absentDays").asInt()).isEqualTo(11);
        assertThat(slip.path("absenceDeduction").decimalValue()).isEqualByComparingTo("4400.00");
        assertThat(slip.path("grossEarnings").decimalValue()).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    void aSecondRunForTheSameMonthIsRefusedAndCreatesNothing() throws Exception {
        JsonNode first = createRunOk(hrToken, MONTH_YEAR, MONTH);
        int created = first.path("payslips").size();

        createRun(hrToken, MONTH_YEAR, MONTH).andExpect(status().isConflict());

        Long runs = jdbc.queryForObject("select count(*) from payroll_runs", Long.class);
        Long slips = jdbc.queryForObject("select count(*) from payslips", Long.class);
        assertThat(runs).isEqualTo(1L);
        assertThat(slips).isEqualTo((long) created);
    }

    @Test
    void theUniqueConstraintItselfRefusesADuplicateMonth() throws Exception {
        createRunOk(hrToken, MONTH_YEAR, MONTH);
        UUID id = UUID.randomUUID();

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update("""
                insert into payroll_runs (id, run_year, run_month, status) values (?, ?, ?, 'DRAFT')
                """, id, MONTH_YEAR, MONTH)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void differentMonthsCoexist() throws Exception {
        createRunOk(hrToken, MONTH_YEAR, MONTH).path("run");
        createRun(hrToken, MONTH_YEAR, MONTH + 1).andExpect(status().isCreated());

        JsonNode page = body(getAs(hrToken, "/api/payroll/runs").andExpect(status().isOk()));
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        // Newest first.
        assertThat(page.path("content").get(0).path("month").asInt()).isEqualTo(MONTH + 1);
    }

    @Test
    void aFutureMonthIsRefused() throws Exception {
        LocalDate next = nextMonth();
        createRun(hrToken, next.getYear(), next.getMonthValue()).andExpect(status().isBadRequest());
    }

    @Test
    void theCurrentMonthIsAllowed() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        createRun(hrToken, today.getYear(), today.getMonthValue()).andExpect(status().isCreated());
    }

    @Test
    void anImpossibleMonthFailsValidation() throws Exception {
        createRun(hrToken, 2025, 13).andExpect(status().isBadRequest());
        createRun(hrToken, 1999, 1).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ state machine

    @Test
    void aDraftRunCanBeRecalculatedAndPicksUpALaterCorrection() throws Exception {
        buildReferenceMarch(hrToken, staff.id());
        UUID runId = runIdOf(createRunOk(hrToken, MONTH_YEAR, MONTH));
        assertThat(payslipOf(runDetail(hrToken, runId), staff.id()).path("absentDays").asInt())
                .isEqualTo(1);

        // HR discovers the missing day and adds it; the draft must follow.
        addSessionOk(hrToken, staff.id(), march(ABSENT_DAY), "09:00", "17:00");
        JsonNode recalculated = body(recalculate(hrToken, runId).andExpect(status().isOk()));

        JsonNode slip = payslipOf(recalculated, staff.id());
        assertThat(slip.path("absentDays").asInt()).isZero();
        assertThat(slip.path("absenceDeduction").decimalValue()).isEqualByComparingTo("0.00");
        // gross 8 800 + 225 + 400 - 400 = 9 025.00
        assertThat(slip.path("grossEarnings").decimalValue()).isEqualByComparingTo("9025.00");
    }

    @Test
    void recalculatingReplacesThePayslipsInsteadOfDuplicatingThem() throws Exception {
        UUID runId = runIdOf(createRunOk(hrToken, MONTH_YEAR, MONTH));
        Long before = jdbc.queryForObject("select count(*) from payslips", Long.class);

        recalculate(hrToken, runId).andExpect(status().isOk());
        recalculate(hrToken, runId).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from payslips", Long.class)).isEqualTo(before);
    }

    @Test
    void finalizingLocksTheRunAgainstRecalculationAndDeletion() throws Exception {
        UUID runId = runIdOf(createRunOk(hrToken, MONTH_YEAR, MONTH));

        JsonNode finalized = body(finalizeRun(hrToken, runId).andExpect(status().isOk()));
        assertThat(finalized.path("status").asText()).isEqualTo("FINALIZED");
        assertThat(finalized.path("finalizedAt").isNull()).isFalse();
        assertThat(finalized.path("finalizedById").isNull()).isFalse();

        recalculate(hrToken, runId).andExpect(status().isConflict());
        deleteRun(hrToken, runId).andExpect(status().isConflict());
        finalizeRun(hrToken, runId).andExpect(status().isConflict());
    }

    @Test
    void attendanceCorrectionsAfterFinalizationDoNotChangeAPayslip() throws Exception {
        buildReferenceMarch(hrToken, staff.id());
        UUID runId = runIdOf(createRunOk(hrToken, MONTH_YEAR, MONTH));
        finalizeRun(hrToken, runId).andExpect(status().isOk());

        addSessionOk(hrToken, staff.id(), march(ABSENT_DAY), "09:00", "20:00");

        JsonNode slip = payslipOf(runDetail(hrToken, runId), staff.id());
        assertThat(slip.path("absentDays").asInt()).isEqualTo(1);
        assertThat(slip.path("overtimeMinutes").asInt()).isEqualTo(180);
        assertThat(slip.path("netPay").decimalValue()).isEqualByComparingTo("6925.60");
    }

    @Test
    void aSalaryChangeAfterTheRunNeverRewritesTheSnapshot() throws Exception {
        UUID runId = runIdOf(createRunOk(hrToken, MONTH_YEAR, MONTH));
        finalizeRun(hrToken, runId).andExpect(status().isOk());

        mvc.perform(patch("/api/employees/" + staff.id()).header("Authorization", bearer(adminToken))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"salary\": 99999.00}"))
                .andExpect(status().isOk());

        JsonNode slip = payslipOf(runDetail(hrToken, runId), staff.id());
        assertThat(slip.path("baseSalary").decimalValue()).isEqualByComparingTo("8800.00");
        assertThat(slip.path("dailyRate").decimalValue()).isEqualByComparingTo("400.00");
    }

    @Test
    void aDraftRunCanBeDeletedWithItsPayslips() throws Exception {
        UUID runId = runIdOf(createRunOk(hrToken, MONTH_YEAR, MONTH));

        deleteRun(hrToken, runId).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from payroll_runs", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from payslips", Long.class)).isZero();
        // ...and the month is free again.
        createRun(hrToken, MONTH_YEAR, MONTH).andExpect(status().isCreated());
    }

    @Test
    void anUnknownRunIsANotFound() throws Exception {
        UUID ghost = UUID.randomUUID();
        getAs(hrToken, "/api/payroll/runs/" + ghost).andExpect(status().isNotFound());
        recalculate(hrToken, ghost).andExpect(status().isNotFound());
        finalizeRun(hrToken, ghost).andExpect(status().isNotFound());
        deleteRun(hrToken, ghost).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ authority

    @Test
    void onlyHrAndAdminMayTouchRuns() throws Exception {
        UUID runId = runIdOf(createRunOk(adminToken, MONTH_YEAR, MONTH));

        createRun(staffToken, MONTH_YEAR, MONTH + 1).andExpect(status().isForbidden());
        getAs(staffToken, "/api/payroll/runs").andExpect(status().isForbidden());
        getAs(staffToken, "/api/payroll/runs/" + runId).andExpect(status().isForbidden());
        recalculate(staffToken, runId).andExpect(status().isForbidden());
        finalizeRun(staffToken, runId).andExpect(status().isForbidden());
        deleteRun(staffToken, runId).andExpect(status().isForbidden());
        getAs(staffToken, "/api/payroll/runs/" + runId + "/export.xlsx").andExpect(status().isForbidden());
    }

    @Test
    void aManagerHasNoMorePayrollAuthorityThanAnyEmployee() throws Exception {
        String managerToken = login(manager.email(), manager.password());
        UUID runId = runIdOf(createRunOk(adminToken, MONTH_YEAR, MONTH));

        createRun(managerToken, MONTH_YEAR, MONTH + 1).andExpect(status().isForbidden());
        getAs(managerToken, "/api/payroll/runs/" + runId).andExpect(status().isForbidden());
    }

    @Test
    void anonymousPayrollAccessIsRejected() throws Exception {
        mvc.perform(post("/api/payroll/runs")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/payroll/payslips/me")).andExpect(status().isUnauthorized());
    }
}
