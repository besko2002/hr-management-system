package com.example.hr.leave;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Holiday CRUD: HR/ADMIN write, everybody reads, and the effect on working days. */
class LeaveHolidayIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private String hrToken;
    private String staffToken;
    private LocalDate firstDate;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        NewEmployee hr = createEmployee(admin, "Hilda Hr", Role.HR, null);
        NewEmployee staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, null);
        hrToken = login(hr.email(), hr.password());
        staffToken = login(staff.email(), staff.password());
        firstDate = planningStart();
    }

    @Test
    void hrCanAddAHoliday() throws Exception {
        JsonNode created = body(addHoliday(hrToken, firstDate, "Revolution Day")
                .andExpect(status().isCreated()));
        assertThat(created.path("date").asText()).isEqualTo(firstDate.toString());
        assertThat(created.path("name").asText()).isEqualTo("Revolution Day");
        assertThat(created.path("id").asText()).isNotBlank();
    }

    @Test
    void adminCanAddAHoliday() throws Exception {
        addHoliday(admin, firstDate, "Sinai Liberation Day").andExpect(status().isCreated());
    }

    @Test
    void aPlainEmployeeCannotAddAHoliday() throws Exception {
        addHoliday(staffToken, firstDate, "Nice try")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only HR or an ADMIN may manage holidays"));
    }

    @Test
    void anyAuthenticatedUserCanListHolidays() throws Exception {
        addHolidayOk(admin, firstDate, "Revolution Day");
        JsonNode holidays = body(getAs(staffToken, "/api/leave/holidays").andExpect(status().isOk()));
        assertThat(holidays).hasSize(1);
        assertThat(holidays.get(0).path("name").asText()).isEqualTo("Revolution Day");
    }

    @Test
    void anUnauthenticatedCallerCannotListHolidays() throws Exception {
        mvc.perform(get("/api/leave/holidays")).andExpect(status().isUnauthorized());
    }

    @Test
    void holidaysAreListedInDateOrder() throws Exception {
        addHolidayOk(admin, firstDate.plusDays(10), "Later");
        addHolidayOk(admin, firstDate, "Earlier");
        JsonNode holidays = body(getAs(staffToken, "/api/leave/holidays").andExpect(status().isOk()));
        assertThat(holidays.get(0).path("name").asText()).isEqualTo("Earlier");
        assertThat(holidays.get(1).path("name").asText()).isEqualTo("Later");
    }

    @Test
    void theListCanBeNarrowedToARange() throws Exception {
        addHolidayOk(admin, firstDate, "Inside");
        addHolidayOk(admin, firstDate.plusDays(40), "Outside");

        JsonNode inRange = body(getAs(staffToken, "/api/leave/holidays?from=" + firstDate
                + "&to=" + firstDate.plusDays(7)).andExpect(status().isOk()));
        assertThat(inRange).hasSize(1);
        assertThat(inRange.get(0).path("name").asText()).isEqualTo("Inside");
    }

    @Test
    void anInvertedRangeIsRejected() throws Exception {
        getAs(staffToken, "/api/leave/holidays?from=" + firstDate + "&to=" + firstDate.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'to' must not be before 'from'"));
    }

    @Test
    void twoHolidaysOnTheSameDateAreRefused() throws Exception {
        addHolidayOk(admin, firstDate, "Revolution Day");
        addHoliday(hrToken, firstDate, "Something else")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A holiday is already registered on " + firstDate));
    }

    @Test
    void aMissingDateOrBlankNameIsRejected() throws Exception {
        mvc.perform(post("/api/leave/holidays").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "No date"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").exists());

        addHoliday(admin, firstDate, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void hrCanRenameAndMoveAHoliday() throws Exception {
        UUID id = addHolidayOk(admin, firstDate, "Provisional");
        JsonNode updated = body(updateHoliday(hrToken, id, firstDate.plusDays(1), "Final")
                .andExpect(status().isOk()));
        assertThat(updated.path("date").asText()).isEqualTo(firstDate.plusDays(1).toString());
        assertThat(updated.path("name").asText()).isEqualTo("Final");
    }

    @Test
    void renamingWithoutMovingIsAllowed() throws Exception {
        UUID id = addHolidayOk(admin, firstDate, "Provisional");
        updateHoliday(admin, id, firstDate, "Renamed only").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed only"));
    }

    @Test
    void movingAHolidayOntoAnotherHolidaysDateIsRefused() throws Exception {
        UUID first = addHolidayOk(admin, firstDate, "First");
        addHolidayOk(admin, firstDate.plusDays(1), "Second");
        updateHoliday(admin, first, firstDate.plusDays(1), "First").andExpect(status().isConflict());
    }

    @Test
    void aPlainEmployeeCannotChangeOrDeleteAHoliday() throws Exception {
        UUID id = addHolidayOk(admin, firstDate, "Revolution Day");
        updateHoliday(staffToken, id, firstDate, "Hacked").andExpect(status().isForbidden());
        deleteHoliday(staffToken, id).andExpect(status().isForbidden());
    }

    @Test
    void hrCanDeleteAHoliday() throws Exception {
        UUID id = addHolidayOk(admin, firstDate, "Temporary");
        deleteHoliday(hrToken, id).andExpect(status().isNoContent());
        assertThat(body(getAs(staffToken, "/api/leave/holidays").andExpect(status().isOk()))).isEmpty();
    }

    @Test
    void changingOrDeletingAnUnknownHolidayIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        deleteHoliday(admin, unknown).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No holiday with id " + unknown));
        updateHoliday(admin, unknown, firstDate, "Nope").andExpect(status().isNotFound());
    }

    @Test
    void aHolidayShortensANewLeaveRequestButNotAnExistingOne() throws Exception {
        NewEmployee employee = createEmployee(admin, "Hana Holidaymaker", Role.EMPLOYEE, null);
        String token = login(employee.email(), employee.password());

        LocalDate start = firstDate;
        LocalDate end = endOf(start, 5);
        UUID before = fileRequestOk(token, LeaveType.ANNUAL, start, end);
        assertThat(body(getAs(token, "/api/leave/requests/" + before)).path("workingDays").asInt())
                .isEqualTo(5);

        // A holiday declared afterwards must not retroactively change what the request costs.
        addHolidayOk(admin, nextWorkingDayAfter(start), "Declared later");
        assertThat(body(getAs(token, "/api/leave/requests/" + before)).path("workingDays").asInt())
                .isEqualTo(5);

        // A new request over the same holiday does see it.
        LocalDate laterStart = nextWorkingDayAfter(end.plusDays(7));
        addHolidayOk(admin, nextWorkingDayAfter(laterStart), "Also later");
        JsonNode created = body(fileRequest(token, LeaveType.ANNUAL, laterStart, endOf(laterStart, 5))
                .andExpect(status().isCreated()));
        assertThat(created.path("workingDays").asInt()).isEqualTo(4);
    }
}
