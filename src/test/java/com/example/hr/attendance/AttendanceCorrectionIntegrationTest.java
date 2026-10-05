package com.example.hr.attendance;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HR corrections: who may make them, what they record and everything they refuse. */
class AttendanceCorrectionIntegrationTest extends AbstractAttendanceIntegrationTest {

    private String adminToken;
    private NewEmployee hr;
    private String hrToken;
    private NewEmployee manager;
    private String managerToken;
    private NewEmployee staff;
    private String staffToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        hr = createEmployee(adminToken, "Hana HR", Role.HR, null);
        manager = createEmployee(adminToken, "Mary Manager", Role.EMPLOYEE, null);
        staff = createEmployee(adminToken, "Sara Staff", Role.EMPLOYEE, manager.id());
        hrToken = login(hr.email(), hr.password());
        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());
    }

    // ------------------------------------------------------------------ happy paths

    @Test
    void hrCanAddAMissingSessionAndItIsStampedAsACorrection() throws Exception {
        JsonNode created = body(addSession(hrToken, staff.id(), at(march(4), "09:00"),
                at(march(4), "17:00"), "forgot to badge in").andExpect(status().isCreated()));

        assertThat(created.path("source").asText()).isEqualTo("HR_CORRECTION");
        assertThat(created.path("correctedById").asText()).isEqualTo(hr.id().toString());
        assertThat(created.path("correctionReason").asText()).isEqualTo("forgot to badge in");
        assertThat(created.path("workDate").asText()).isEqualTo("2025-03-04");
        assertThat(created.path("minutes").asInt()).isEqualTo(480);
    }

    @Test
    void correctingASelfSessionFlipsItsSourceAndRecordsTheCorrector() throws Exception {
        checkIn(staffToken).andExpect(status().isCreated());
        JsonNode closed = body(checkOut(staffToken).andExpect(status().isOk()));
        UUID sessionId = UUID.fromString(closed.path("id").asText());
        assertThat(closed.path("source").asText()).isEqualTo("SELF");

        JsonNode corrected = body(correctSession(hrToken, sessionId, at(march(5), "08:50"),
                at(march(5), "17:10"), "clock drift").andExpect(status().isOk()));

        assertThat(corrected.path("id").asText()).isEqualTo(sessionId.toString());
        assertThat(corrected.path("source").asText()).isEqualTo("HR_CORRECTION");
        assertThat(corrected.path("correctedById").asText()).isEqualTo(hr.id().toString());
        assertThat(corrected.path("workDate").asText()).isEqualTo("2025-03-05");
        assertThat(corrected.path("minutes").asInt()).isEqualTo(500);
    }

    @Test
    void anAddedSessionImmediatelyChangesTheDerivedDay() throws Exception {
        addSessionOk(hrToken, staff.id(), march(4), "09:45", "18:00");

        JsonNode range = body(attendanceOf(hrToken, staff.id(), march(4), march(4))
                .andExpect(status().isOk()));
        JsonNode day = day(range, march(4));
        assertThat(day.path("status").asText()).isEqualTo("LATE");
        assertThat(day.path("lateMinutes").asInt()).isEqualTo(30);
        assertThat(day.path("workedMinutes").asInt()).isEqualTo(495);
        assertThat(day.path("overtimeMinutes").asInt()).isEqualTo(60);
        assertThat(day.path("sessions")).hasSize(1);
    }

    @Test
    void anOpenSessionCanBeClosedByACorrection() throws Exception {
        UUID sessionId = addSessionOk(hrToken, staff.id(), march(6), "09:00", null);
        JsonNode fixed = body(correctSession(hrToken, sessionId, at(march(6), "09:00"),
                at(march(6), "17:00"), "closed from the door log").andExpect(status().isOk()));

        assertThat(fixed.path("minutes").asInt()).isEqualTo(480);
        JsonNode range = body(attendanceOf(hrToken, staff.id(), march(6), march(6))
                .andExpect(status().isOk()));
        assertThat(day(range, march(6)).path("status").asText()).isEqualTo("PRESENT");
    }

    @Test
    void anAdminMayCorrectToo() throws Exception {
        addSession(adminToken, staff.id(), at(march(4), "09:00"), at(march(4), "17:00"), "admin fix")
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ authority

    @Test
    void aManagerMayNotCorrectTheirReportsAttendance() throws Exception {
        addSession(managerToken, staff.id(), at(march(4), "09:00"), at(march(4), "17:00"), "nope")
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeMayNotCorrectTheirOwnAttendance() throws Exception {
        addSession(staffToken, staff.id(), at(march(4), "09:00"), at(march(4), "17:00"), "nope")
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeMayNotCorrectAnExistingSessionEither() throws Exception {
        UUID sessionId = addSessionOk(hrToken, staff.id(), march(4), "09:00", "17:00");
        correctSession(staffToken, sessionId, at(march(4), "08:00"), at(march(4), "19:00"), "nope")
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ validation

    @Test
    void aReasonIsMandatoryOnEveryCorrection() throws Exception {
        addSession(hrToken, staff.id(), at(march(4), "09:00"), at(march(4), "17:00"), null)
                .andExpect(status().isBadRequest());
        addSession(hrToken, staff.id(), at(march(4), "09:00"), at(march(4), "17:00"), "   ")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCheckOutAtOrBeforeTheCheckInIsRejected() throws Exception {
        addSession(hrToken, staff.id(), at(march(4), "17:00"), at(march(4), "09:00"), "reversed")
                .andExpect(status().isBadRequest());
        addSession(hrToken, staff.id(), at(march(4), "09:00"), at(march(4), "09:00"), "zero length")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSessionInTheFutureIsRejected() throws Exception {
        Instant tomorrow = Instant.now().plusSeconds(86_400);
        addSession(hrToken, staff.id(), tomorrow, null, "time travel")
                .andExpect(status().isBadRequest());
        addSession(hrToken, staff.id(), Instant.now().minusSeconds(60), tomorrow, "time travel")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSessionLongerThanTwentyFourHoursIsRejected() throws Exception {
        addSession(hrToken, staff.id(), at(march(4), "09:00"), at(march(5), "09:01"), "too long")
                .andExpect(status().isBadRequest());
        // Exactly 24 hours is still accepted.
        addSession(hrToken, staff.id(), at(march(10), "09:00"), at(march(11), "09:00"), "exactly a day")
                .andExpect(status().isCreated());
    }

    @Test
    void aSessionBeforeTheHireDateIsRejected() throws Exception {
        NewEmployee joiner = createEmployee(adminToken, "Jana Joiner", Role.EMPLOYEE, null, null,
                "5000.00", "2025-03-10");
        addSession(hrToken, joiner.id(), at(march(4), "09:00"), at(march(4), "17:00"), "before hire")
                .andExpect(status().isBadRequest());
        addSession(hrToken, joiner.id(), at(march(11), "09:00"), at(march(11), "17:00"), "after hire")
                .andExpect(status().isCreated());
    }

    @Test
    void anUnknownEmployeeOrSessionIsANotFound() throws Exception {
        addSession(hrToken, UUID.randomUUID(), at(march(4), "09:00"), at(march(4), "17:00"), "ghost")
                .andExpect(status().isNotFound());
        correctSession(hrToken, UUID.randomUUID(), at(march(4), "09:00"), at(march(4), "17:00"), "ghost")
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ overlaps

    @Test
    void anOverlappingSessionOfTheSameEmployeeIsAConflict() throws Exception {
        addSessionOk(hrToken, staff.id(), march(4), "09:00", "13:00");

        addSession(hrToken, staff.id(), at(march(4), "12:00"), at(march(4), "15:00"), "overlap")
                .andExpect(status().isConflict());
        addSession(hrToken, staff.id(), at(march(4), "08:00"), at(march(4), "10:00"), "overlap")
                .andExpect(status().isConflict());
        addSession(hrToken, staff.id(), at(march(4), "10:00"), at(march(4), "11:00"), "contained")
                .andExpect(status().isConflict());
        addSession(hrToken, staff.id(), at(march(4), "08:00"), at(march(4), "18:00"), "containing")
                .andExpect(status().isConflict());
    }

    @Test
    void touchingSessionsDoNotOverlap() throws Exception {
        addSessionOk(hrToken, staff.id(), march(4), "09:00", "13:00");
        addSession(hrToken, staff.id(), at(march(4), "13:00"), at(march(4), "17:00"), "back from lunch")
                .andExpect(status().isCreated());
    }

    @Test
    void twoEmployeesCanHaveTheExactSameInterval() throws Exception {
        NewEmployee other = createEmployee(adminToken, "Omar Other", Role.EMPLOYEE, null);
        addSessionOk(hrToken, staff.id(), march(4), "09:00", "17:00");
        addSession(hrToken, other.id(), at(march(4), "09:00"), at(march(4), "17:00"), "same shift")
                .andExpect(status().isCreated());
    }

    @Test
    void aCorrectionIgnoresTheRowItIsEditingButNotTheOthers() throws Exception {
        UUID morning = addSessionOk(hrToken, staff.id(), march(4), "09:00", "13:00");
        addSessionOk(hrToken, staff.id(), march(4), "14:00", "17:00");

        // Shrinking the morning session in place is fine...
        correctSession(hrToken, morning, at(march(4), "09:30"), at(march(4), "12:30"), "shrunk")
                .andExpect(status().isOk());
        // ...but stretching it into the afternoon one is a conflict.
        correctSession(hrToken, morning, at(march(4), "09:30"), at(march(4), "15:00"), "stretched")
                .andExpect(status().isConflict());
    }

    @Test
    void anOverlapWithAnOpenSessionIsDetectedOnItsCheckInInstant() throws Exception {
        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        addSession(hrToken, staff.id(), at(yesterday, "09:00"), null, "open")
                .andExpect(status().isCreated());

        addSession(hrToken, staff.id(), at(yesterday, "08:00"), at(yesterday, "10:00"), "straddles it")
                .andExpect(status().isConflict());
        addSession(hrToken, staff.id(), at(yesterday, "10:00"), at(yesterday, "12:00"), "after it")
                .andExpect(status().isCreated());
    }
}
