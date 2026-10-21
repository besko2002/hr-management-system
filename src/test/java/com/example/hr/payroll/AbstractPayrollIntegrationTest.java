package com.example.hr.payroll;

import com.example.hr.attendance.AbstractAttendanceIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared fixtures for the payroll API tests, including the <b>reference March 2025
 * scenario</b> whose every figure is hand-computed in the test comments.
 *
 * <p>March 2025 has 22 working days (Friday+Saturday weekend, no holidays). With a base of
 * 8 800 the daily rate is exactly 400.00 and the hourly rate exactly 50.00, which keeps all
 * downstream arithmetic exact.
 */
public abstract class AbstractPayrollIntegrationTest extends AbstractAttendanceIntegrationTest {

    /** Base salary that makes the March 2025 rates whole: 8800 / 22 = 400.00 a day. */
    protected static final String REFERENCE_SALARY = "8800.00";

    /** The 22 working days of March 2025. */
    protected static final List<Integer> MARCH_WORKING_DAYS =
            List.of(2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 16, 17, 18, 19, 20, 23, 24, 25, 26, 27, 30, 31);

    protected static final int LATE_DAY_ONE = 4;
    protected static final int LATE_DAY_TWO = 11;
    protected static final int OVERTIME_DAY = 17;
    protected static final int UNPAID_LEAVE_DAY = 19;
    protected static final int ABSENT_DAY = 26;
    /** 2025-03-08 is a Saturday. */
    protected static final int WEEKEND_WORK_DAY = 8;

    /**
     * Writes a full month of attendance for one employee:
     * 17 plain 09:00–17:00 days, two days arriving at 09:45 (30 late minutes each),
     * one day worked to 20:00 (180 overtime minutes), one APPROVED <em>unpaid</em> leave
     * day, one plain absence and four hours of Saturday work (240 holiday minutes).
     */
    protected void buildReferenceMarch(String hrToken, UUID employeeId) throws Exception {
        approvedLeave(employeeId, "UNPAID", march(UNPAID_LEAVE_DAY), march(UNPAID_LEAVE_DAY), 1);
        for (int dayOfMonth : MARCH_WORKING_DAYS) {
            if (dayOfMonth == UNPAID_LEAVE_DAY || dayOfMonth == ABSENT_DAY) {
                continue;
            }
            if (dayOfMonth == LATE_DAY_ONE || dayOfMonth == LATE_DAY_TWO) {
                addSessionOk(hrToken, employeeId, march(dayOfMonth), "09:45", "17:00");
            } else if (dayOfMonth == OVERTIME_DAY) {
                addSessionOk(hrToken, employeeId, march(dayOfMonth), "09:00", "20:00");
            } else {
                addSessionOk(hrToken, employeeId, march(dayOfMonth), "09:00", "17:00");
            }
        }
        addSessionOk(hrToken, employeeId, march(WEEKEND_WORK_DAY), "10:00", "14:00");
    }

    // ------------------------------------------------------------------ runs

    protected ResultActions createRun(String token, int year, int month) throws Exception {
        return mvc.perform(post("/api/payroll/runs").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("year", year, "month", month))));
    }

    protected JsonNode createRunOk(String token, int year, int month) throws Exception {
        return body(createRun(token, year, month).andExpect(status().isCreated()));
    }

    protected UUID runIdOf(JsonNode runDetail) {
        return UUID.fromString(runDetail.path("run").path("id").asText());
    }

    protected ResultActions recalculate(String token, UUID runId) throws Exception {
        return mvc.perform(post("/api/payroll/runs/" + runId + "/recalculate")
                .header("Authorization", bearer(token)));
    }

    protected ResultActions finalizeRun(String token, UUID runId) throws Exception {
        return mvc.perform(post("/api/payroll/runs/" + runId + "/finalize")
                .header("Authorization", bearer(token)));
    }

    protected ResultActions deleteRun(String token, UUID runId) throws Exception {
        return mvc.perform(delete("/api/payroll/runs/" + runId).header("Authorization", bearer(token)));
    }

    protected JsonNode runDetail(String token, UUID runId) throws Exception {
        return body(getAs(token, "/api/payroll/runs/" + runId).andExpect(status().isOk()));
    }

    // ------------------------------------------------------------------ payslips

    /** The payslip of one employee out of a run detail payload. */
    protected JsonNode payslipOf(JsonNode runDetail, UUID employeeId) {
        for (JsonNode node : runDetail.path("payslips")) {
            if (employeeId.toString().equals(node.path("employeeId").asText())) {
                return node;
            }
        }
        throw new AssertionError("The run has no payslip for " + employeeId);
    }

    protected UUID payslipIdOf(JsonNode runDetail, UUID employeeId) {
        return UUID.fromString(payslipOf(runDetail, employeeId).path("id").asText());
    }

    protected ResultActions payslip(String token, UUID payslipId) throws Exception {
        return getAs(token, "/api/payroll/payslips/" + payslipId);
    }

    protected ResultActions payslipPdf(String token, UUID payslipId) throws Exception {
        return getAs(token, "/api/payroll/payslips/" + payslipId + "/pdf");
    }

    protected JsonNode myPayslips(String token) throws Exception {
        return body(getAs(token, "/api/payroll/payslips/me").andExpect(status().isOk()));
    }

    /** A month far enough ahead to be refused as "in the future" whenever the suite runs. */
    protected LocalDate nextMonth() {
        return LocalDate.now(ZONE).withDayOfMonth(1).plusMonths(1);
    }
}
