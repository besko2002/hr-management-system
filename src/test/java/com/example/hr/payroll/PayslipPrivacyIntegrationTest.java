package com.example.hr.payroll;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The payslip privacy matrix. A manager sees their report's attendance but <b>never</b>
 * their pay, and everything that is refused answers 404 rather than 403 so a payslip id
 * cannot be probed.
 */
class PayslipPrivacyIntegrationTest extends AbstractPayrollIntegrationTest {

    private String adminToken;
    private String hrToken;
    private NewEmployee director;
    private String directorToken;
    private NewEmployee manager;
    private String managerToken;
    private NewEmployee staff;
    private String staffToken;
    private NewEmployee stranger;
    private String strangerToken;

    private UUID runId;
    private UUID payslipId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        NewEmployee hr = createEmployee(adminToken, "Hana HR", Role.HR, null);
        director = createEmployee(adminToken, "Dina Director", Role.EMPLOYEE, null);
        manager = createEmployee(adminToken, "Mary Manager", Role.EMPLOYEE, director.id());
        staff = createEmployee(adminToken, "Sara Staff", Role.EMPLOYEE, manager.id(), null,
                REFERENCE_SALARY);
        stranger = createEmployee(adminToken, "Sam Stranger", Role.EMPLOYEE, null);

        hrToken = login(hr.email(), hr.password());
        directorToken = login(director.email(), director.password());
        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());
        strangerToken = login(stranger.email(), stranger.password());

        JsonNode run = createRunOk(hrToken, MONTH_YEAR, MONTH);
        runId = runIdOf(run);
        payslipId = payslipIdOf(run, staff.id());
    }

    // ------------------------------------------------------------------ while DRAFT

    @Test
    void anEmployeeCannotSeeTheirOwnPayslipWhileTheRunIsStillADraft() throws Exception {
        payslip(staffToken, payslipId).andExpect(status().isNotFound());
        assertThat(myPayslips(staffToken)).isEmpty();
    }

    @Test
    void hrAndAdminSeeADraftPayslipImmediately() throws Exception {
        payslip(hrToken, payslipId).andExpect(status().isOk());
        payslip(adminToken, payslipId).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ once FINALIZED

    @Test
    void onceFinalizedTheEmployeeSeesTheirOwnPayslip() throws Exception {
        finalizeRun(hrToken, runId).andExpect(status().isOk());

        JsonNode own = body(payslip(staffToken, payslipId).andExpect(status().isOk()));
        assertThat(own.path("employeeId").asText()).isEqualTo(staff.id().toString());
        assertThat(own.path("netPay").isNull()).isFalse();

        JsonNode mine = myPayslips(staffToken);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).path("id").asText()).isEqualTo(payslipId.toString());
        assertThat(mine.get(0).path("runStatus").asText()).isEqualTo("FINALIZED");
    }

    @Test
    void myPayslipsOnlyEverListsFinalizedRuns() throws Exception {
        finalizeRun(hrToken, runId).andExpect(status().isOk());
        // A second, still draft month must not appear.
        createRunOk(hrToken, MONTH_YEAR, MONTH + 1);

        JsonNode mine = myPayslips(staffToken);
        assertThat(mine).hasSize(1);
        assertThat(mine).allMatch(slip -> "FINALIZED".equals(slip.path("runStatus").asText()));
    }

    @Test
    void theDirectManagerNeverSeesAReportsPayslipEvenWhenFinalized() throws Exception {
        finalizeRun(hrToken, runId).andExpect(status().isOk());

        payslip(managerToken, payslipId).andExpect(status().isNotFound());
        payslipPdf(managerToken, payslipId).andExpect(status().isNotFound());
    }

    @Test
    void noAncestorManagerSeesItEither() throws Exception {
        finalizeRun(hrToken, runId).andExpect(status().isOk());
        payslip(directorToken, payslipId).andExpect(status().isNotFound());
    }

    @Test
    void anotherEmployeeGetsNotFound() throws Exception {
        finalizeRun(hrToken, runId).andExpect(status().isOk());
        payslip(strangerToken, payslipId).andExpect(status().isNotFound());
    }

    @Test
    void hrSeesEveryPayslipOfTheRun() throws Exception {
        JsonNode detail = runDetail(hrToken, runId);
        assertThat(detail.path("payslips")).hasSize(5); // HR, director, manager, staff, stranger
        for (JsonNode slip : detail.path("payslips")) {
            payslip(hrToken, UUID.fromString(slip.path("id").asText())).andExpect(status().isOk());
        }
    }

    @Test
    void anUnknownPayslipIdIsNotFoundForEverybody() throws Exception {
        UUID ghost = UUID.randomUUID();
        payslip(hrToken, ghost).andExpect(status().isNotFound());
        payslip(staffToken, ghost).andExpect(status().isNotFound());
        payslipPdf(staffToken, ghost).andExpect(status().isNotFound());
    }

    @Test
    void anEmployeeWithNoPayslipAtAllGetsAnEmptyListNotAnError() throws Exception {
        // The stranger has the default salary, so they do have a payslip; use the
        // bootstrap admin instead, who has none because they have no salary.
        assertThat(myPayslips(adminToken)).isEmpty();
    }

    @Test
    void theEmployeeOnlyEverSeesTheirOwnPayslipNotAColleaguesInTheSameRun() throws Exception {
        finalizeRun(hrToken, runId).andExpect(status().isOk());
        UUID colleague = payslipIdOf(runDetail(hrToken, runId), stranger.id());

        payslip(staffToken, colleague).andExpect(status().isNotFound());
        payslip(staffToken, payslipId).andExpect(status().isOk());
    }
}
