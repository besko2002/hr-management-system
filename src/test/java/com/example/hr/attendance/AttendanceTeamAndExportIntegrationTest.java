package com.example.hr.attendance;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /team/today} and the monthly {@code export.xlsx}. */
class AttendanceTeamAndExportIntegrationTest extends AbstractAttendanceIntegrationTest {

    private String adminToken;
    private NewEmployee director;
    private String directorToken;
    private NewEmployee manager;
    private String managerToken;
    private NewEmployee present;
    private NewEmployee absent;
    private String presentToken;
    private String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        NewEmployee hr = createEmployee(adminToken, "Hana HR", Role.HR, null);
        director = createEmployee(adminToken, "Dina Director", Role.EMPLOYEE, null);
        manager = createEmployee(adminToken, "Mary Manager", Role.EMPLOYEE, director.id());
        present = createEmployee(adminToken, "Pia Present", Role.EMPLOYEE, manager.id());
        absent = createEmployee(adminToken, "Abe Absent", Role.EMPLOYEE, manager.id());

        hrToken = login(hr.email(), hr.password());
        directorToken = login(director.email(), director.password());
        managerToken = login(manager.email(), manager.password());
        presentToken = login(present.email(), present.password());
    }

    private JsonNode teamToday(String token) throws Exception {
        return body(getAs(token, "/api/attendance/team/today").andExpect(status().isOk()));
    }

    // ------------------------------------------------------------------ team/today

    @Test
    void theManagerSeesWhoOfTheirPeopleIsCurrentlyIn() throws Exception {
        checkIn(presentToken).andExpect(status().isCreated());

        JsonNode team = teamToday(managerToken);
        assertThat(team.path("teamSize").asInt()).isEqualTo(2);
        assertThat(team.path("inCount").asInt()).isEqualTo(1);

        JsonNode inside = member(team, present.id().toString());
        assertThat(inside.path("currentlyIn").asBoolean()).isTrue();
        assertThat(inside.path("firstIn").isNull()).isFalse();
        JsonNode outside = member(team, absent.id().toString());
        assertThat(outside.path("currentlyIn").asBoolean()).isFalse();
        assertThat(outside.path("firstIn").isNull()).isTrue();
    }

    @Test
    void theTeamViewSpansTheWholeSubtreeNotJustDirectReports() throws Exception {
        JsonNode team = teamToday(directorToken);
        // manager + the manager's two reports.
        assertThat(team.path("teamSize").asInt()).isEqualTo(3);
        assertThat(ids(team)).contains(manager.id().toString(), present.id().toString(),
                absent.id().toString());
    }

    @Test
    void anEmployeeWithoutReportsSeesAnEmptyTeam() throws Exception {
        JsonNode team = teamToday(presentToken);
        assertThat(team.path("teamSize").asInt()).isZero();
        assertThat(team.path("members")).isEmpty();
    }

    @Test
    void hrSeesEveryActiveEmployeeIncludingThemself() throws Exception {
        JsonNode team = teamToday(hrToken);
        // admin + HR + director + manager + two staff.
        assertThat(team.path("teamSize").asInt()).isEqualTo(6);
    }

    @Test
    void onAWorkingDayWithoutAnySessionTheTeamIsReportedAbsent() throws Exception {
        JsonNode team = teamToday(managerToken);
        LocalDate today = LocalDate.now(ZONE);
        boolean restDay = !"WORKING".equals(team.path("dayKind").asText());

        if (restDay) {
            assertThat(team.path("absentCount").asInt()).isZero();
        } else {
            assertThat(team.path("absentCount").asInt()).isEqualTo(2);
            assertThat(member(team, absent.id().toString()).path("status").asText()).isEqualTo("ABSENT");
        }
        assertThat(team.path("day").asText()).isEqualTo(today.toString());
    }

    @Test
    void aTeamMemberOnApprovedLeaveTodayIsReportedOnLeave() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        approvedLeave(absent.id(), "UNPAID", today, today, 1);

        JsonNode team = teamToday(managerToken);
        JsonNode onLeave = member(team, absent.id().toString());
        if ("WORKING".equals(team.path("dayKind").asText())) {
            assertThat(onLeave.path("status").asText()).isEqualTo("ON_LEAVE");
            assertThat(onLeave.path("leave").asText()).isEqualTo("UNPAID");
            assertThat(team.path("onLeaveCount").asInt()).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ export

    @Test
    void theMonthlyExportHasOneRowPerEmployeeDayAndIsReadableByPoi() throws Exception {
        addSessionOk(adminToken, present.id(), march(4), "09:45", "18:00");
        addSessionOk(adminToken, present.id(), march(8), "10:00", "14:00"); // a Saturday

        byte[] bytes = getAs(hrToken, "/api/attendance/export.xlsx?year=2025&month=3")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Attendance 2025-03");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Employee no");
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("Status");

            List<Row> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                rows.add(sheet.getRow(i));
            }
            // Six employees exist; five of them were hired on 2024-01-15 and the bootstrap
            // admin on the day the suite runs, so March 2025 yields 5 x 31 rows.
            assertThat(rows).hasSize(5 * 31);

            Row late = rows.stream()
                    .filter(row -> row.getCell(1).getStringCellValue().equals("Pia Present")
                            && row.getCell(3).getStringCellValue().equals("2025-03-04"))
                    .findFirst().orElseThrow();
            assertThat(late.getCell(5).getStringCellValue()).isEqualTo("WORKING");
            assertThat(late.getCell(6).getStringCellValue()).isEqualTo("LATE");
            assertThat(late.getCell(8).getStringCellValue()).isEqualTo("09:45");
            assertThat(late.getCell(9).getStringCellValue()).isEqualTo("18:00");
            assertThat((int) late.getCell(11).getNumericCellValue()).isEqualTo(495);
            assertThat((int) late.getCell(13).getNumericCellValue()).isEqualTo(30);
            assertThat((int) late.getCell(14).getNumericCellValue()).isEqualTo(60);

            Row saturday = rows.stream()
                    .filter(row -> row.getCell(1).getStringCellValue().equals("Pia Present")
                            && row.getCell(3).getStringCellValue().equals("2025-03-08"))
                    .findFirst().orElseThrow();
            assertThat(saturday.getCell(5).getStringCellValue()).isEqualTo("WEEKEND");
            assertThat((int) saturday.getCell(15).getNumericCellValue()).isEqualTo(240);
        }
    }

    @Test
    void theExportIsRestrictedToHrAndAdmin() throws Exception {
        getAs(managerToken, "/api/attendance/export.xlsx?year=2025&month=3")
                .andExpect(status().isForbidden());
        getAs(login(present.email(), present.password()), "/api/attendance/export.xlsx?year=2025&month=3")
                .andExpect(status().isForbidden());
        getAs(adminToken, "/api/attendance/export.xlsx?year=2025&month=3").andExpect(status().isOk());
    }

    @Test
    void anImpossibleMonthIsRejected() throws Exception {
        getAs(hrToken, "/api/attendance/export.xlsx?year=2025&month=13")
                .andExpect(status().isBadRequest());
        getAs(hrToken, "/api/attendance/export.xlsx?year=1999&month=1")
                .andExpect(status().isBadRequest());
    }

    private static JsonNode member(JsonNode team, String employeeId) {
        for (JsonNode node : team.path("members")) {
            if (employeeId.equals(node.path("employeeId").asText())) {
                return node;
            }
        }
        throw new AssertionError("The team view does not contain " + employeeId);
    }

    private static List<String> ids(JsonNode team) {
        List<String> ids = new ArrayList<>();
        team.path("members").forEach(node -> ids.add(node.path("employeeId").asText()));
        return ids;
    }
}
