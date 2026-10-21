package com.example.hr.leave;

import com.example.hr.employee.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Overlap rules. A day is "taken" by a PENDING or an APPROVED request only; a REJECTED or
 * CANCELLED request frees its days again. Boundaries are inclusive, so adjacent ranges are
 * fine while a single shared day is not.
 */
class LeaveOverlapIntegrationTest extends AbstractLeaveIntegrationTest {

    private String admin;
    private String staffToken;
    private String managerToken;
    private NewEmployee manager;
    private UUID existingRequest;

    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, null);
        NewEmployee staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());

        start = planningStart();
        end = endOf(start, 5);
        existingRequest = fileRequestOk(staffToken, LeaveType.ANNUAL, start, end);
    }

    @Test
    void anAdjacentRangeStartingTheDayAfterIsAllowed() throws Exception {
        fileRequest(staffToken, LeaveType.ANNUAL, end.plusDays(1), endOf(end.plusDays(1), 2))
                .andExpect(status().isCreated());
    }

    @Test
    void anAdjacentRangeEndingTheDayBeforeIsAllowed() throws Exception {
        LocalDate laterStart = end.plusDays(8);
        fileRequestOk(staffToken, LeaveType.ANNUAL, laterStart, endOf(laterStart, 2));
        // Ends exactly one day before the later request starts: no shared day, so allowed.
        fileRequest(staffToken, LeaveType.ANNUAL, laterStart.minusDays(3), laterStart.minusDays(1))
                .andExpect(status().isCreated());
    }

    @Test
    void anIdenticalRangeIsRejected() throws Exception {
        fileRequest(staffToken, LeaveType.ANNUAL, start, end)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This range overlaps one of your pending or approved leave requests"));
    }

    @Test
    void aRangeSharingOnlyTheLastDayIsRejected() throws Exception {
        fileRequest(staffToken, LeaveType.ANNUAL, end, end.plusDays(6)).andExpect(status().isConflict());
    }

    @Test
    void aRangeInsideTheExistingOneIsRejected() throws Exception {
        LocalDate inside = nextWorkingDayAfter(start);
        fileRequest(staffToken, LeaveType.ANNUAL, inside, inside).andExpect(status().isConflict());
    }

    @Test
    void aWiderRangeCoveringTheExistingOneIsRejected() throws Exception {
        fileRequest(staffToken, LeaveType.ANNUAL, start, end.plusDays(3)).andExpect(status().isConflict());
    }

    @Test
    void anOverlapWithADifferentLeaveTypeIsAlsoRejected() throws Exception {
        fileRequest(staffToken, LeaveType.SICK, start, end).andExpect(status().isConflict());
    }

    @Test
    void anOverlapWithAnApprovedRequestIsRejected() throws Exception {
        approve(managerToken, existingRequest).andExpect(status().isOk());
        fileRequest(staffToken, LeaveType.ANNUAL, start, end).andExpect(status().isConflict());
    }

    @Test
    void anOverlapWithACancelledRequestIsAllowed() throws Exception {
        cancel(staffToken, existingRequest).andExpect(status().isOk());
        fileRequest(staffToken, LeaveType.ANNUAL, start, end).andExpect(status().isCreated());
    }

    @Test
    void anOverlapWithARejectedRequestIsAllowed() throws Exception {
        reject(managerToken, existingRequest, "Too busy that week").andExpect(status().isOk());
        fileRequest(staffToken, LeaveType.ANNUAL, start, end).andExpect(status().isCreated());
    }

    @Test
    void overlapIsCheckedPerEmployeeNotCompanyWide() throws Exception {
        NewEmployee colleague = createEmployee(admin, "Cleo Colleague", Role.EMPLOYEE, manager.id());
        String colleagueToken = login(colleague.email(), colleague.password());
        fileRequest(colleagueToken, LeaveType.ANNUAL, start, end).andExpect(status().isCreated());
    }
}
