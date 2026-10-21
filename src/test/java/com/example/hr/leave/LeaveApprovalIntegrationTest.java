package com.example.hr.leave;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may decide a leave request, and what every decision does to the balance.
 *
 * <pre>
 *   Cora Ceo (no manager)
 *     └── Dirk Director
 *           └── Mary Manager
 *                 └── Stan Staff
 *   Ulla Unrelated (no manager, own branch)
 *   Hilda Hr (HR)
 * </pre>
 */
class LeaveApprovalIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private NewEmployee ceo;
    private NewEmployee director;
    private NewEmployee manager;
    private NewEmployee staff;

    private String ceoToken;
    private String directorToken;
    private String managerToken;
    private String staffToken;
    private String unrelatedToken;
    private String hrToken;
    private NewEmployee hr;

    private LocalDate start;
    private LocalDate end;
    private UUID request;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        ceo = createEmployee(admin, "Cora Ceo", Role.EMPLOYEE, null);
        director = createEmployee(admin, "Dirk Director", Role.EMPLOYEE, ceo.id());
        manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, director.id());
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        NewEmployee unrelated = createEmployee(admin, "Ulla Unrelated", Role.EMPLOYEE, null);
        hr = createEmployee(admin, "Hilda Hr", Role.HR, null);

        ceoToken = login(ceo.email(), ceo.password());
        directorToken = login(director.email(), director.password());
        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());
        unrelatedToken = login(unrelated.email(), unrelated.password());
        hrToken = login(hr.email(), hr.password());

        start = planningStart();
        end = endOf(start, 5);
        request = fileRequestOk(staffToken, LeaveType.ANNUAL, start, end);
    }

    // ------------------------------------------------------------------ authority matrix

    @Test
    void theDirectManagerCanApprove() throws Exception {
        JsonNode decided = body(approve(managerToken, request).andExpect(status().isOk()));
        assertThat(decided.path("status").asText()).isEqualTo("APPROVED");
        assertThat(decided.path("decidedById").asText()).isEqualTo(manager.id().toString());
        assertThat(decided.path("decidedByName").asText()).isEqualTo("Mary Manager");
        assertThat(decided.path("decidedAt").isNull()).isFalse();
    }

    @Test
    void theDirectManagerCanReject() throws Exception {
        JsonNode decided = body(reject(managerToken, request, "Sprint ends that week")
                .andExpect(status().isOk()));
        assertThat(decided.path("status").asText()).isEqualTo("REJECTED");
        assertThat(decided.path("decisionNote").asText()).isEqualTo("Sprint ends that week");
    }

    @Test
    void anAncestorWhoIsNotTheDirectManagerCannotDecide() throws Exception {
        // Dirk and Cora are above Stan, so the request is visible to them — but only Stan's
        // direct manager (Mary) or HR may decide it.
        approve(directorToken, request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Only Stan Staff's direct manager or HR may decide this request"));
        approve(ceoToken, request).andExpect(status().isForbidden());
    }

    @Test
    void anUnrelatedEmployeeGetsNotFoundInsteadOfForbidden() throws Exception {
        approve(unrelatedToken, request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No leave request with id " + request));
        reject(unrelatedToken, request, "no").andExpect(status().isNotFound());
    }

    @Test
    void hrCanAlwaysOverrideAndApprove() throws Exception {
        approve(hrToken, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.decidedByName").value("Hilda Hr"));
    }

    @Test
    void adminCanAlwaysOverrideAndReject() throws Exception {
        reject(admin, request, "Head count").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void theRequesterCannotDecideTheirOwnRequest() throws Exception {
        approve(staffToken, request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "You cannot decide your own leave request; cancel it instead"));
    }

    @Test
    void hrCannotDecideTheirOwnRequest() throws Exception {
        UUID hrRequest = fileDaysOk(hrToken, LeaveType.ANNUAL, start, 2);
        approve(hrToken, hrRequest)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "You cannot decide your own leave request; cancel it instead"));
        reject(hrToken, hrRequest, "nope").andExpect(status().isForbidden());
    }

    @Test
    void adminCannotDecideTheirOwnRequest() throws Exception {
        UUID adminRequest = fileDaysOk(admin, LeaveType.ANNUAL, start, 2);
        approve(admin, adminRequest).andExpect(status().isForbidden());
    }

    @Test
    void aManagerCannotDecideTheRequestOfSomebodyWhoIsNotTheirDirectReport() throws Exception {
        UUID directorRequest = fileDaysOk(directorToken, LeaveType.ANNUAL, start, 2);
        // Mary reports to Dirk, so Dirk's request is not visible to her at all.
        approve(managerToken, directorRequest).andExpect(status().isNotFound());
    }

    @Test
    void anEmployeeWithoutAManagerIsDecidedByHr() throws Exception {
        UUID ceoRequest = fileDaysOk(ceoToken, LeaveType.ANNUAL, start, 3);
        approve(hrToken, ceoRequest).andExpect(status().isOk());
    }

    @Test
    void anEmployeeWithoutAManagerCannotBeDecidedByTheirOwnReports() throws Exception {
        UUID ceoRequest = fileDaysOk(ceoToken, LeaveType.ANNUAL, start, 3);
        approve(directorToken, ceoRequest).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownRequestIdIsNotFound() throws Exception {
        approve(managerToken, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ decision rules

    @Test
    void rejectingWithoutANoteIsRejected() throws Exception {
        reject(managerToken, request, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "decisionNote is required when rejecting a leave request"));
        reject(managerToken, request, "   ").andExpect(status().isBadRequest());
    }

    @Test
    void aDecisionNoteLongerThanFiveHundredCharactersIsRejected() throws Exception {
        reject(managerToken, request, "x".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.decisionNote").exists());
    }

    @Test
    void approvingTwiceIsAConflict() throws Exception {
        approve(managerToken, request).andExpect(status().isOk());
        approve(managerToken, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This leave request is APPROVED and can no longer become APPROVED"));
    }

    @Test
    void rejectingAnAlreadyApprovedRequestIsAConflict() throws Exception {
        approve(managerToken, request).andExpect(status().isOk());
        reject(managerToken, request, "changed my mind").andExpect(status().isConflict());
    }

    @Test
    void approvingAnAlreadyRejectedRequestIsAConflict() throws Exception {
        reject(managerToken, request, "no").andExpect(status().isOk());
        approve(managerToken, request).andExpect(status().isConflict());
    }

    @Test
    void decidingACancelledRequestIsAConflict() throws Exception {
        cancel(staffToken, request).andExpect(status().isOk());
        approve(managerToken, request).andExpect(status().isConflict());
        reject(managerToken, request, "no").andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ balance accounting

    @Test
    void approvalMovesTheDaysFromPendingToUsed() throws Exception {
        approve(managerToken, request).andExpect(status().isOk());

        JsonNode balance = balance(staffToken, LeaveType.ANNUAL, planningYear());
        assertThat(balance.path("entitledDays").asInt()).isEqualTo(21);
        assertThat(balance.path("pendingDays").asInt()).isZero();
        assertThat(balance.path("usedDays").asInt()).isEqualTo(5);
        assertThat(balance.path("remainingDays").asInt()).isEqualTo(16);
    }

    @Test
    void rejectionRefundsTheReservedDaysExactly() throws Exception {
        reject(managerToken, request, "Not this week").andExpect(status().isOk());

        JsonNode balance = balance(staffToken, LeaveType.ANNUAL, planningYear());
        assertThat(balance.path("pendingDays").asInt()).isZero();
        assertThat(balance.path("usedDays").asInt()).isZero();
        assertThat(balance.path("remainingDays").asInt()).isEqualTo(21);
    }

    @Test
    void theBalanceSurvivesAFullApproveCancelRefileCycleUnchanged() throws Exception {
        int before = remaining(staffToken, LeaveType.ANNUAL, planningYear());
        approve(managerToken, request).andExpect(status().isOk());
        cancel(staffToken, request).andExpect(status().isOk());

        JsonNode balance = balance(staffToken, LeaveType.ANNUAL, planningYear());
        assertThat(balance.path("usedDays").asInt()).isZero();
        assertThat(balance.path("pendingDays").asInt()).isZero();
        assertThat(balance.path("remainingDays").asInt()).isEqualTo(before + 5);
    }

    // ------------------------------------------------------------------ visibility of one request

    @Test
    void theRequesterTheirAncestorsAndHrCanReadTheRequest() throws Exception {
        for (String token : new String[] {staffToken, managerToken, directorToken, ceoToken, hrToken, admin}) {
            getAs(token, "/api/leave/requests/" + request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(request.toString()));
        }
    }

    @Test
    void anUnrelatedEmployeeCannotReadTheRequest() throws Exception {
        getAs(unrelatedToken, "/api/leave/requests/" + request).andExpect(status().isNotFound());
    }

    @Test
    void aDescendantCannotReadTheirManagersRequest() throws Exception {
        UUID managerRequest = fileDaysOk(managerToken, LeaveType.ANNUAL, start, 2);
        getAs(staffToken, "/api/leave/requests/" + managerRequest).andExpect(status().isNotFound());
    }

    @Test
    void theRequestDetailKeepsTheReasonForThoseWhoMaySeeIt() throws Exception {
        LocalDate laterStart = nextWorkingDayAfter(end.plusDays(7));
        JsonNode created = body(fileRequest(staffToken, LeaveType.ANNUAL, laterStart, laterStart, "Dentist")
                .andExpect(status().isCreated()));
        getAs(managerToken, "/api/leave/requests/" + created.path("id").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Dentist"));
    }
}
