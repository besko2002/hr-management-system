package com.example.hr.access;

import com.example.hr.common.ForbiddenException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.Role;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests for the access policy; ancestry is stubbed, no database involved. */
class EmployeeAccessPolicyTest {

    private static Employee employee(Role role) {
        return new Employee("EMP-0001", "Person " + role, role + "@hr.local", "hash", role,
                "Title", null, null, LocalDate.of(2024, 1, 1), null, false);
    }

    /** Stub: the given pairs (ancestor -> employee) are considered ancestry links. */
    private static EmployeeAccessPolicy policyWithAncestors(Set<String> pairs) {
        AncestryChecker checker = (ancestorId, employeeId) -> pairs.contains(ancestorId + ">" + employeeId);
        return new EmployeeAccessPolicy(checker);
    }

    private static EmployeeAccessPolicy policyWithNoAncestry() {
        return policyWithAncestors(Set.of());
    }

    private static EmployeeAccessPolicy policyWhereFirstIsAncestorOfSecond(UUID ancestor, UUID descendant) {
        return policyWithAncestors(Set.of(ancestor + ">" + descendant));
    }

    // ------------------------------------------------------------------ profile visibility

    @Test
    void employeeCanViewTheirOwnProfile() {
        Employee self = employee(Role.EMPLOYEE);
        assertThat(policyWithNoAncestry().canViewProfile(self, self)).isTrue();
    }

    @Test
    void hrCanViewAnyProfile() {
        assertThat(policyWithNoAncestry().canViewProfile(employee(Role.HR), employee(Role.EMPLOYEE))).isTrue();
    }

    @Test
    void adminCanViewAnyProfile() {
        assertThat(policyWithNoAncestry().canViewProfile(employee(Role.ADMIN), employee(Role.EMPLOYEE))).isTrue();
    }

    @Test
    void anAncestorManagerCanViewTheProfile() {
        Employee manager = employee(Role.EMPLOYEE);
        Employee report = employee(Role.EMPLOYEE);
        assertThat(policyWhereFirstIsAncestorOfSecond(manager.getId(), report.getId())
                .canViewProfile(manager, report)).isTrue();
    }

    @Test
    void anUnrelatedEmployeeCannotViewTheProfile() {
        assertThat(policyWithNoAncestry().canViewProfile(employee(Role.EMPLOYEE), employee(Role.EMPLOYEE))).isFalse();
    }

    @Test
    void anUnrelatedEmployeeGetsNotFoundRatherThanForbidden() {
        assertThatThrownBy(() -> policyWithNoAncestry()
                .requireCanViewProfile(employee(Role.EMPLOYEE), employee(Role.EMPLOYEE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireCanViewProfilePassesForHr() {
        assertThatCode(() -> policyWithNoAncestry().requireCanViewProfile(employee(Role.HR), employee(Role.EMPLOYEE)))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ salary visibility

    @Test
    void employeeSeesTheirOwnSalary() {
        Employee self = employee(Role.EMPLOYEE);
        assertThat(policyWithNoAncestry().canSeeSalary(self, self)).isTrue();
    }

    @Test
    void hrSeesEverybodysSalary() {
        assertThat(policyWithNoAncestry().canSeeSalary(employee(Role.HR), employee(Role.EMPLOYEE))).isTrue();
    }

    @Test
    void adminSeesEverybodysSalary() {
        assertThat(policyWithNoAncestry().canSeeSalary(employee(Role.ADMIN), employee(Role.EMPLOYEE))).isTrue();
    }

    @Test
    void aManagerNeverSeesAReportsSalary() {
        Employee manager = employee(Role.EMPLOYEE);
        Employee report = employee(Role.EMPLOYEE);
        EmployeeAccessPolicy policy = policyWhereFirstIsAncestorOfSecond(manager.getId(), report.getId());
        assertThat(policy.canViewProfile(manager, report)).isTrue();
        assertThat(policy.canSeeSalary(manager, report)).isFalse();
    }

    // ------------------------------------------------------------------ write access

    @Test
    void aPlainEmployeeMayNotManageAnybody() {
        assertThatThrownBy(() -> policyWithNoAncestry()
                .requireCanManage(employee(Role.EMPLOYEE), employee(Role.EMPLOYEE), "update"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void hrMayManageAPlainEmployee() {
        assertThatCode(() -> policyWithNoAncestry()
                .requireCanManage(employee(Role.HR), employee(Role.EMPLOYEE), "update"))
                .doesNotThrowAnyException();
    }

    @Test
    void hrMayNotManageAnAdmin() {
        assertThatThrownBy(() -> policyWithNoAncestry()
                .requireCanManage(employee(Role.HR), employee(Role.ADMIN), "update"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    void adminMayManageAnAdmin() {
        assertThatCode(() -> policyWithNoAncestry()
                .requireCanManage(employee(Role.ADMIN), employee(Role.ADMIN), "update"))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ role assignment

    @Test
    void hrMayNotGrantTheAdminRole() {
        assertThatThrownBy(() -> policyWithNoAncestry()
                .requireCanAssignRole(employee(Role.HR), null, Role.ADMIN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("grant the ADMIN role");
    }

    @Test
    void adminMayGrantTheAdminRole() {
        assertThatCode(() -> policyWithNoAncestry().requireCanAssignRole(employee(Role.ADMIN), null, Role.ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    void hrMayCreateHrAndEmployeeAccounts() {
        EmployeeAccessPolicy policy = policyWithNoAncestry();
        assertThatCode(() -> policy.requireCanAssignRole(employee(Role.HR), null, Role.HR))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCanAssignRole(employee(Role.HR), null, Role.EMPLOYEE))
                .doesNotThrowAnyException();
    }

    @Test
    void hrMayNotRevokeTheAdminRoleOfSomebodyElse() {
        assertThatThrownBy(() -> policyWithNoAncestry()
                .requireCanAssignRole(employee(Role.HR), employee(Role.ADMIN), Role.EMPLOYEE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminMayRevokeTheAdminRoleOfSomebodyElse() {
        assertThatCode(() -> policyWithNoAncestry()
                .requireCanAssignRole(employee(Role.ADMIN), employee(Role.ADMIN), Role.HR))
                .doesNotThrowAnyException();
    }

    @Test
    void nobodyMayChangeTheirOwnRole() {
        Employee hr = employee(Role.HR);
        assertThatThrownBy(() -> policyWithNoAncestry().requireCanAssignRole(hr, hr, Role.EMPLOYEE))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("your own role");

        Employee admin = employee(Role.ADMIN);
        assertThatThrownBy(() -> policyWithNoAncestry().requireCanAssignRole(admin, admin, Role.HR))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("your own role");
    }

    @Test
    void keepingYourOwnRoleUnchangedIsAllowed() {
        Employee hr = employee(Role.HR);
        assertThatCode(() -> policyWithNoAncestry().requireCanAssignRole(hr, hr, Role.HR))
                .doesNotThrowAnyException();
    }

    @Test
    void aPlainEmployeeMayNotAssignRolesAtAll() {
        assertThatThrownBy(() -> policyWithNoAncestry()
                .requireCanAssignRole(employee(Role.EMPLOYEE), null, Role.EMPLOYEE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireHrOrAdminNamesTheRefusedAction() {
        assertThatThrownBy(() -> policyWithNoAncestry().requireHrOrAdmin(employee(Role.EMPLOYEE), "list employees"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("list employees");
    }
}
