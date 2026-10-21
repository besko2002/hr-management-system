package com.example.hr.report;

import com.example.hr.attendance.AbstractAttendanceIntegrationTest;
import com.example.hr.employee.Role;
import com.example.hr.leave.LeaveType;
import com.example.hr.leave.WorkingDayCalculator;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportIntegrationTest extends AbstractAttendanceIntegrationTest {

    private static final WorkingDayCalculator CALENDAR =
            new WorkingDayCalculator(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));

    @Test
    void headcountGroupsByDepartmentAndStatus() throws Exception {
        String admin = adminToken();
        UUID eng = createDepartment(admin, "Engineering");
        UUID sales = createDepartment(admin, "Sales");
        createEmployee(admin, "Eng One", Role.EMPLOYEE, null, eng, "5000.00");
        createEmployee(admin, "Eng Two", Role.EMPLOYEE, null, eng, "5100.00");
        NewEmployee toFire = createEmployee(admin, "Sales One", Role.EMPLOYEE, null, sales, "4000.00");
        mvc.perform(post("/api/employees/" + toFire.id() + "/terminate")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        JsonNode rows = body(getAs(admin, "/api/reports/headcount").andExpect(status().isOk()));
        assertThat(countOf(rows, "Engineering", "ACTIVE")).isEqualTo(2);
        assertThat(countOf(rows, "Sales", "TERMINATED")).isEqualTo(1);
        assertThat(countOf(rows, "Unassigned", "ACTIVE")).isGreaterThanOrEqualTo(1);

        byte[] xlsx = getAs(admin, "/api/reports/headcount.xlsx")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = sheet.getRow(0);
            assertThat(fmt.formatCellValue(header.getCell(0))).isEqualTo("Department");
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(1);
        }
    }

    @Test
    void leaveSummaryAggregatesUsedPendingAndRemaining() throws Exception {
        String admin = adminToken();
        UUID dept = createDepartment(admin, "LeaveDept");
        NewEmployee manager = createEmployee(admin, "Leave Manager", Role.EMPLOYEE, null, dept, "8000.00",
                "2024-01-15");
        NewEmployee staff = createEmployee(admin, "Leave Staff", Role.EMPLOYEE, manager.id(), dept, "5000.00",
                "2024-01-15");
        String staffToken = login(staff.email(), staff.password());
        String managerToken = login(manager.email(), manager.password());

        LocalDate start = CALENDAR.nextWorkingDay(LocalDate.now().plusMonths(1).withDayOfMonth(1), List.of());
        LocalDate end = CALENDAR.endDateFor(start, 2, List.of());
        int year = start.getYear();

        getAs(staffToken, "/api/leave/balances/me?year=" + year).andExpect(status().isOk());

        Map<String, Object> request = new HashMap<>();
        request.put("type", LeaveType.ANNUAL.name());
        request.put("startDate", start.toString());
        request.put("endDate", end.toString());
        JsonNode created = body(mvc.perform(post("/api/leave/requests").header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()));
        UUID requestId = UUID.fromString(created.path("id").asText());
        int usedDays = created.path("workingDays").asInt();

        mvc.perform(post("/api/leave/requests/" + requestId + "/approve")
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        JsonNode rows = body(getAs(admin, "/api/reports/leave-summary?year=" + year)
                .andExpect(status().isOk()));
        JsonNode annual = findLeave(rows, "LeaveDept", "ANNUAL");
        assertThat(annual.path("usedDays").asInt()).isEqualTo(usedDays);
        assertThat(annual.path("pendingDays").asInt()).isEqualTo(0);
        assertThat(annual.path("remainingDays").asInt()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void payrollSummaryOnlyCountsFinalizedRuns() throws Exception {
        String admin = adminToken();
        NewEmployee hr = createEmployee(admin, "Pay Hr", Role.HR, null);
        String hrToken = login(hr.email(), hr.password());

        NewEmployee staff = createEmployee(hrToken, "March Worker", Role.EMPLOYEE, null, null, "8800.00",
                "2024-01-15");
        for (int day = 1; day <= 31; day++) {
            LocalDate date = march(day);
            if (date.getDayOfWeek() == DayOfWeek.FRIDAY || date.getDayOfWeek() == DayOfWeek.SATURDAY) {
                continue;
            }
            addSessionOk(hrToken, staff.id(), date, "09:00", "17:00");
        }

        JsonNode draft = body(mvc.perform(post("/api/payroll/runs").header("Authorization", bearer(hrToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("year", MONTH_YEAR, "month", MONTH))))
                .andExpect(status().isCreated()));
        UUID runId = UUID.fromString(draft.path("run").path("id").asText());

        JsonNode before = body(getAs(hrToken, "/api/reports/payroll-summary?year=" + MONTH_YEAR)
                .andExpect(status().isOk()));
        assertThat(monthMissing(before, MONTH)).isTrue();

        mvc.perform(post("/api/payroll/runs/" + runId + "/finalize")
                        .header("Authorization", bearer(hrToken)))
                .andExpect(status().isOk());

        JsonNode after = body(getAs(hrToken, "/api/reports/payroll-summary?year=" + MONTH_YEAR)
                .andExpect(status().isOk()));
        JsonNode marchRow = findMonth(after, MONTH);
        assertThat(marchRow.path("payslipCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(new BigDecimal(marchRow.path("netPay").asText())).isPositive();
    }

    @Test
    void attendanceSummaryCountsLateAbsentAndOvertimeByDepartment() throws Exception {
        String admin = adminToken();
        UUID dept = createDepartment(admin, "Ops");
        NewEmployee worker = createEmployee(admin, "Ops Worker", Role.EMPLOYEE, null, dept, "5000.00",
                "2024-01-15");

        addSessionOk(admin, worker.id(), march(3), "09:30", "17:00");
        addSessionOk(admin, worker.id(), march(4), "09:00", "19:00");

        JsonNode rows = body(getAs(admin, "/api/reports/attendance-summary?year=" + MONTH_YEAR + "&month=" + MONTH)
                .andExpect(status().isOk()));
        JsonNode ops = findAttendance(rows, "Ops");
        assertThat(ops.path("lateDays").asLong()).isEqualTo(1);
        assertThat(ops.path("absentDays").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(ops.path("overtimeMinutes").asLong()).isEqualTo(120);
    }

    @Test
    void reportsAreHrAdminOnly() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Report Nosy", Role.EMPLOYEE, null);
        String staffToken = login(staff.email(), staff.password());

        getAs(staffToken, "/api/reports/headcount").andExpect(status().isForbidden());
        getAs(staffToken, "/api/reports/leave-summary?year=2025").andExpect(status().isForbidden());
        getAs(staffToken, "/api/reports/payroll-summary?year=2025").andExpect(status().isForbidden());
        getAs(staffToken, "/api/reports/attendance-summary?year=2025&month=3")
                .andExpect(status().isForbidden());
    }

    private static long countOf(JsonNode rows, String department, String status) {
        for (JsonNode row : rows) {
            if (department.equals(row.path("department").asText())
                    && status.equals(row.path("status").asText())) {
                return row.path("count").asLong();
            }
        }
        return 0;
    }

    private static JsonNode findLeave(JsonNode rows, String department, String type) {
        for (JsonNode row : rows) {
            if (department.equals(row.path("department").asText())
                    && type.equals(row.path("leaveType").asText())) {
                return row;
            }
        }
        throw new AssertionError("No leave row for " + department + "/" + type + " in " + rows);
    }

    private static JsonNode findMonth(JsonNode rows, int month) {
        for (JsonNode row : rows) {
            if (row.path("month").asInt() == month) {
                return row;
            }
        }
        throw new AssertionError("No payroll month " + month + " in " + rows);
    }

    private static boolean monthMissing(JsonNode rows, int month) {
        for (JsonNode row : rows) {
            if (row.path("month").asInt() == month) {
                return false;
            }
        }
        return true;
    }

    private static JsonNode findAttendance(JsonNode rows, String department) {
        for (JsonNode row : rows) {
            if (department.equals(row.path("department").asText())) {
                return row;
            }
        }
        throw new AssertionError("No attendance row for " + department + " in " + rows);
    }
}
