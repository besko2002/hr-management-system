package com.example.hr.leave;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The listing endpoints: "my requests" with its filters and paging, and the approval queue
 * (direct reports for a manager, everything for HR/ADMIN).
 */
class LeaveQueryIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private NewEmployee manager;
    private NewEmployee staff;
    private String managerToken;
    private String staffToken;
    private String hrToken;
    private String otherManagerToken;
    private final List<UUID> staffRequests = new ArrayList<>();
    private final List<LocalDate> starts = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, null);
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        NewEmployee hr = createEmployee(admin, "Hilda Hr", Role.HR, null);
        NewEmployee otherManager = createEmployee(admin, "Owen Othermanager", Role.EMPLOYEE, null);
        NewEmployee otherStaff = createEmployee(admin, "Oscar Otherstaff", Role.EMPLOYEE, otherManager.id());

        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());
        hrToken = login(hr.email(), hr.password());
        otherManagerToken = login(otherManager.email(), otherManager.password());
        String otherStaffToken = login(otherStaff.email(), otherStaff.password());

        // Five one-day requests for Stan, in increasing date order.
        LocalDate slot = planningStart();
        for (int i = 0; i < 5; i++) {
            starts.add(slot);
            staffRequests.add(fileRequestOk(staffToken, LeaveType.ANNUAL, slot, slot));
            slot = nextWorkingDayAfter(slot);
        }
        // One request in the other branch, so scope errors become visible.
        fileRequestOk(otherStaffToken, LeaveType.ANNUAL, planningStart(), planningStart());
    }

    private JsonNode page(String token, String query) throws Exception {
        return body(getAs(token, "/api/leave/requests/me" + query).andExpect(status().isOk()));
    }

    // ------------------------------------------------------------------ my requests

    @Test
    void myRequestsReturnsOnlyMyOwnRequests() throws Exception {
        JsonNode page = page(staffToken, "");
        assertThat(page.path("totalElements").asInt()).isEqualTo(5);
        for (JsonNode node : page.path("content")) {
            assertThat(node.path("employeeId").asText()).isEqualTo(staff.id().toString());
        }
    }

    @Test
    void myRequestsIsSortedByStartDateDescending() throws Exception {
        JsonNode content = page(staffToken, "").path("content");
        assertThat(content.get(0).path("startDate").asText())
                .isEqualTo(starts.get(starts.size() - 1).toString());
        assertThat(content.get(4).path("startDate").asText()).isEqualTo(starts.get(0).toString());
    }

    @Test
    void myRequestsIsPaged() throws Exception {
        JsonNode first = page(staffToken, "?page=0&size=2");
        assertThat(first.path("content")).hasSize(2);
        assertThat(first.path("page").asInt()).isZero();
        assertThat(first.path("size").asInt()).isEqualTo(2);
        assertThat(first.path("totalElements").asInt()).isEqualTo(5);
        assertThat(first.path("totalPages").asInt()).isEqualTo(3);

        JsonNode last = page(staffToken, "?page=2&size=2");
        assertThat(last.path("content")).hasSize(1);
        assertThat(last.path("page").asInt()).isEqualTo(2);
    }

    @Test
    void anOutOfRangePageIsEmptyRatherThanAnError() throws Exception {
        assertThat(page(staffToken, "?page=9&size=2").path("content")).isEmpty();
    }

    @Test
    void theRequestedPageSizeIsCapped() throws Exception {
        assertThat(page(staffToken, "?size=5000").path("size").asInt()).isEqualTo(100);
        assertThat(page(staffToken, "?size=0").path("size").asInt()).isEqualTo(20);
        assertThat(page(staffToken, "?page=-3").path("page").asInt()).isZero();
    }

    @Test
    void myRequestsCanBeFilteredByStatus() throws Exception {
        approve(managerToken, staffRequests.get(0)).andExpect(status().isOk());
        reject(managerToken, staffRequests.get(1), "no").andExpect(status().isOk());

        assertThat(page(staffToken, "?status=PENDING").path("totalElements").asInt()).isEqualTo(3);
        assertThat(page(staffToken, "?status=APPROVED").path("totalElements").asInt()).isEqualTo(1);
        assertThat(page(staffToken, "?status=REJECTED").path("totalElements").asInt()).isEqualTo(1);
        assertThat(page(staffToken, "?status=CANCELLED").path("totalElements").asInt()).isZero();
    }

    @Test
    void anUnknownStatusFilterIsRejected() throws Exception {
        getAs(staffToken, "/api/leave/requests/me?status=MAYBE").andExpect(status().isBadRequest());
    }

    @Test
    void myRequestsCanBeFilteredByYear() throws Exception {
        assertThat(page(staffToken, "?year=" + planningYear()).path("totalElements").asInt()).isEqualTo(5);
        assertThat(page(staffToken, "?year=" + (planningYear() + 1)).path("totalElements").asInt()).isZero();
    }

    @Test
    void statusAndYearFiltersCombine() throws Exception {
        approve(managerToken, staffRequests.get(0)).andExpect(status().isOk());
        assertThat(page(staffToken, "?status=APPROVED&year=" + planningYear())
                .path("totalElements").asInt()).isEqualTo(1);
        assertThat(page(staffToken, "?status=APPROVED&year=" + (planningYear() + 1))
                .path("totalElements").asInt()).isZero();
    }

    @Test
    void anEmployeeWithoutRequestsGetsAnEmptyPage() throws Exception {
        JsonNode page = page(managerToken, "");
        assertThat(page.path("content")).isEmpty();
        assertThat(page.path("totalElements").asInt()).isZero();
    }

    // ------------------------------------------------------------------ approval queue

    @Test
    void aManagerSeesOnlyTheirDirectReportsPendingRequests() throws Exception {
        JsonNode pending = body(getAs(managerToken, "/api/leave/requests/pending")
                .andExpect(status().isOk()));
        assertThat(pending.path("totalElements").asInt()).isEqualTo(5);
        for (JsonNode node : pending.path("content")) {
            assertThat(node.path("employeeName").asText()).isEqualTo("Stan Staff");
        }
    }

    @Test
    void aManagerOfAnotherBranchSeesOnlyTheirOwn() throws Exception {
        JsonNode pending = body(getAs(otherManagerToken, "/api/leave/requests/pending")
                .andExpect(status().isOk()));
        assertThat(pending.path("totalElements").asInt()).isEqualTo(1);
        assertThat(pending.path("content").get(0).path("employeeName").asText()).isEqualTo("Oscar Otherstaff");
    }

    @Test
    void anEmployeeWithoutReportsSeesAnEmptyQueue() throws Exception {
        assertThat(body(getAs(staffToken, "/api/leave/requests/pending").andExpect(status().isOk()))
                .path("totalElements").asInt()).isZero();
    }

    @Test
    void hrAndAdminSeeEveryPendingRequest() throws Exception {
        assertThat(body(getAs(hrToken, "/api/leave/requests/pending").andExpect(status().isOk()))
                .path("totalElements").asInt()).isEqualTo(6);
        assertThat(body(getAs(admin, "/api/leave/requests/pending").andExpect(status().isOk()))
                .path("totalElements").asInt()).isEqualTo(6);
    }

    @Test
    void theQueueOnlyHoldsPendingRequests() throws Exception {
        approve(managerToken, staffRequests.get(0)).andExpect(status().isOk());
        reject(managerToken, staffRequests.get(1), "no").andExpect(status().isOk());
        cancel(staffToken, staffRequests.get(2)).andExpect(status().isOk());

        assertThat(body(getAs(managerToken, "/api/leave/requests/pending").andExpect(status().isOk()))
                .path("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void theQueueIsPaged() throws Exception {
        JsonNode first = body(getAs(managerToken, "/api/leave/requests/pending?page=0&size=2")
                .andExpect(status().isOk()));
        assertThat(first.path("content")).hasSize(2);
        assertThat(first.path("totalPages").asInt()).isEqualTo(3);
        assertThat(body(getAs(managerToken, "/api/leave/requests/pending?page=2&size=2")
                .andExpect(status().isOk())).path("content")).hasSize(1);
    }

    @Test
    void anUnauthenticatedCallerSeesNoQueue() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/leave/requests/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
