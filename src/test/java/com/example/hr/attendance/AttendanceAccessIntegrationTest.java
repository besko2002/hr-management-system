package com.example.hr.attendance;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may read whose attendance. The tree is
 * {@code director -> manager -> staff}, plus an unrelated employee and an HR account.
 */
class AttendanceAccessIntegrationTest extends AbstractAttendanceIntegrationTest {

    private String adminToken;
    private NewEmployee director;
    private String directorToken;
    private NewEmployee manager;
    private String managerToken;
    private NewEmployee staff;
    private String staffToken;
    private NewEmployee stranger;
    private String strangerToken;
    private String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        director = createEmployee(adminToken, "Dina Director", Role.EMPLOYEE, null);
        manager = createEmployee(adminToken, "Mary Manager", Role.EMPLOYEE, director.id());
        staff = createEmployee(adminToken, "Sara Staff", Role.EMPLOYEE, manager.id());
        stranger = createEmployee(adminToken, "Sam Stranger", Role.EMPLOYEE, null);
        NewEmployee hr = createEmployee(adminToken, "Hana HR", Role.HR, null);

        directorToken = login(director.email(), director.password());
        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());
        strangerToken = login(stranger.email(), stranger.password());
        hrToken = login(hr.email(), hr.password());

        addSessionOk(adminToken, staff.id(), march(4), "09:00", "17:00");
    }

    @Test
    void theEmployeeThemselfSeesTheirOwnDays() throws Exception {
        attendanceOf(staffToken, staff.id(), march(1), march(31)).andExpect(status().isOk());
        myAttendance(staffToken, march(1), march(31)).andExpect(status().isOk());
    }

    @Test
    void theDirectManagerSeesTheirReportsDays() throws Exception {
        JsonNode range = body(attendanceOf(managerToken, staff.id(), march(1), march(31))
                .andExpect(status().isOk()));
        assertThat(range.path("employeeId").asText()).isEqualTo(staff.id().toString());
    }

    @Test
    void anyAncestorManagerSeesThemToo() throws Exception {
        attendanceOf(directorToken, staff.id(), march(1), march(31)).andExpect(status().isOk());
    }

    @Test
    void hrAndAdminSeeEverybody() throws Exception {
        attendanceOf(hrToken, staff.id(), march(1), march(31)).andExpect(status().isOk());
        attendanceOf(adminToken, staff.id(), march(1), march(31)).andExpect(status().isOk());
    }

    @Test
    void anUnrelatedEmployeeGetsNotFoundRatherThanForbidden() throws Exception {
        attendanceOf(strangerToken, staff.id(), march(1), march(31)).andExpect(status().isNotFound());
    }

    @Test
    void aReportCannotLookUpwardsAtTheirManager() throws Exception {
        attendanceOf(staffToken, manager.id(), march(1), march(31)).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownEmployeeIdIsAlsoNotFound() throws Exception {
        attendanceOf(hrToken, UUID.randomUUID(), march(1), march(31)).andExpect(status().isNotFound());
    }

    @Test
    void noAttendancePayloadEverContainsASalary() throws Exception {
        String asManager = attendanceOf(managerToken, staff.id(), march(1), march(31))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String asHr = attendanceOf(hrToken, staff.id(), march(1), march(31))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String team = getAs(managerToken, "/api/attendance/team/today")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(asManager).doesNotContain("salary").doesNotContain("netPay");
        assertThat(asHr).doesNotContain("salary").doesNotContain("netPay");
        assertThat(team).doesNotContain("salary").doesNotContain("netPay");
    }

    @Test
    void anonymousAccessIsRejected() throws Exception {
        mvc.perform(get("/api/attendance/me?from=" + march(1) + "&to=" + march(31)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attendance/team/today")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ range guards

    @Test
    void aRangeWhereToPrecedesFromIsRejected() throws Exception {
        myAttendance(staffToken, march(10), march(4)).andExpect(status().isBadRequest());
    }

    @Test
    void aRangeLongerThanNinetyTwoDaysIsRejected() throws Exception {
        LocalDate from = march(1);
        myAttendance(staffToken, from, from.plusDays(92)).andExpect(status().isBadRequest());
        // 92 days inclusive is the largest accepted range.
        myAttendance(staffToken, from, from.plusDays(91)).andExpect(status().isOk());
    }

    @Test
    void daysBeforeTheHireDateAreNotReportedAsAbsences() throws Exception {
        NewEmployee joiner = createEmployee(adminToken, "Jana Joiner", Role.EMPLOYEE, null, null,
                "5000.00", "2025-03-17");
        String joinerToken = login(joiner.email(), joiner.password());

        JsonNode range = body(myAttendance(joinerToken, march(1), march(31)).andExpect(status().isOk()));
        assertThat(range.path("days").get(0).path("day").asText()).isEqualTo("2025-03-17");
        assertThat(range.path("absentDays").asInt()).isEqualTo(11); // 17,18,19,20,23..27,30,31 minus weekends
    }

    @Test
    void aFutureRangeYieldsNoDaysAtAllRatherThanAbsences() throws Exception {
        LocalDate tomorrow = LocalDate.now(ZONE).plusDays(1);
        JsonNode range = body(myAttendance(staffToken, tomorrow, tomorrow.plusDays(10))
                .andExpect(status().isOk()));

        assertThat(range.path("days")).isEmpty();
        assertThat(range.path("absentDays").asInt()).isZero();
    }
}
