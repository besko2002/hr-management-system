package com.example.hr;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every cell of the access matrix for GET /api/employees/{id}, plus the role-granting rules.
 *
 * <pre>
 *   Cora Ceo
 *     ├── Dirk Director ── Mary Manager ── Stan Staff
 *     └── Diana Director ── Mike Manager ── Sven Staff   (the other branch)
 * </pre>
 */
class AccessMatrixIntegrationTest extends AbstractIntegrationTest {

    private String admin;
    private NewEmployee ceo;
    private NewEmployee director;
    private NewEmployee manager;
    private NewEmployee staff;
    private NewEmployee otherDirector;
    private NewEmployee otherManager;
    private NewEmployee otherStaff;
    private NewEmployee hr;

    @BeforeEach
    void buildTree() throws Exception {
        admin = adminToken();
        hr = createEmployee(admin, "Hana Hr", Role.HR, null, null, "11000.00");
        ceo = createEmployee(admin, "Cora Ceo", Role.EMPLOYEE, null, null, "20000.00");
        director = createEmployee(admin, "Dirk Director", Role.EMPLOYEE, ceo.id(), null, "15000.00");
        manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, director.id(), null, "10000.00");
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id(), null, "5000.00");
        otherDirector = createEmployee(admin, "Diana Director", Role.EMPLOYEE, ceo.id(), null, "14000.00");
        otherManager = createEmployee(admin, "Mike Manager", Role.EMPLOYEE, otherDirector.id(), null, "9000.00");
        otherStaff = createEmployee(admin, "Sven Staff", Role.EMPLOYEE, otherManager.id(), null, "4800.00");
    }

    private String tokenOf(NewEmployee employee) throws Exception {
        return login(employee.email(), employee.password());
    }

    // ------------------------------------------------------------------ salary visible

    @Test
    void theEmployeeThemselfSeesTheirProfileWithSalary() throws Exception {
        JsonNode self = body(getAs(tokenOf(staff), "/api/employees/" + staff.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Stan Staff"))
                .andExpect(jsonPath("$.salary").exists()));
        assertThat(self.path("salary").decimalValue()).isEqualByComparingTo("5000.00");
    }

    @Test
    void hrSeesAnybodysProfileWithSalary() throws Exception {
        String hrToken = tokenOf(hr);
        JsonNode view = body(getAs(hrToken, "/api/employees/" + staff.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary").exists()));
        assertThat(view.path("salary").decimalValue()).isEqualByComparingTo("5000.00");

        getAs(hrToken, "/api/employees/" + ceo.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary").exists());
    }

    @Test
    void adminSeesAnybodysProfileWithSalary() throws Exception {
        getAs(admin, "/api/employees/" + staff.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary").exists());
        getAs(admin, "/api/employees/" + otherStaff.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary").exists());
    }

    // ------------------------------------------------------------------ salary hidden

    @Test
    void theDirectManagerSeesTheReportButWithoutTheSalaryField() throws Exception {
        JsonNode view = body(getAs(tokenOf(manager), "/api/employees/" + staff.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Stan Staff"))
                .andExpect(jsonPath("$.email").value(staff.email()))
                .andExpect(jsonPath("$.salary").doesNotExist()));
        assertThat(view.has("salary")).isFalse();
        assertThat(view.toString()).doesNotContain("salary");
    }

    @Test
    void aHigherAncestorSeesTheReportWithoutTheSalaryField() throws Exception {
        getAs(tokenOf(director), "/api/employees/" + staff.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary").doesNotExist());
        getAs(tokenOf(ceo), "/api/employees/" + staff.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Stan Staff"))
                .andExpect(jsonPath("$.salary").doesNotExist());
    }

    @Test
    void aManagerStillSeesTheirOwnSalary() throws Exception {
        String managerToken = tokenOf(manager);
        getAs(managerToken, "/api/employees/" + manager.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary").exists());
        getAs(managerToken, "/api/employees/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary").exists());
    }

    // ------------------------------------------------------------------ not found

    @Test
    void anUnrelatedEmployeeGetsNotFoundRatherThanForbidden() throws Exception {
        getAs(tokenOf(staff), "/api/employees/" + otherStaff.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No employee with id " + otherStaff.id()));
    }

    @Test
    void aSiblingInTheSameTeamIsNotVisible() throws Exception {
        NewEmployee sibling = createEmployee(admin, "Sibyl Sibling", Role.EMPLOYEE, manager.id());
        getAs(tokenOf(staff), "/api/employees/" + sibling.id()).andExpect(status().isNotFound());
    }

    @Test
    void aManagerFromAnotherBranchIsNotVisible() throws Exception {
        // Mike manages the other branch: Stan is invisible to him, and Mary is invisible to Mike's reports.
        getAs(tokenOf(otherManager), "/api/employees/" + staff.id()).andExpect(status().isNotFound());
        getAs(tokenOf(otherStaff), "/api/employees/" + manager.id()).andExpect(status().isNotFound());
    }

    @Test
    void lookingDownwardsIsAllowedButLookingUpwardsIsNot() throws Exception {
        getAs(tokenOf(manager), "/api/employees/" + staff.id()).andExpect(status().isOk());
        getAs(tokenOf(staff), "/api/employees/" + manager.id()).andExpect(status().isNotFound());
        getAs(tokenOf(staff), "/api/employees/" + ceo.id()).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownIdIsNotFoundForEverybody() throws Exception {
        UUID unknown = UUID.randomUUID();
        getAs(admin, "/api/employees/" + unknown).andExpect(status().isNotFound());
        getAs(tokenOf(hr), "/api/employees/" + unknown).andExpect(status().isNotFound());
        getAs(tokenOf(staff), "/api/employees/" + unknown).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ role rules

    @Test
    void hrCannotCreateAnAdmin() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Wannabe Admin");
        body.put("email", "wannabe.admin@hr.local");
        body.put("role", "ADMIN");
        body.put("hireDate", "2024-02-02");

        mvc.perform(post("/api/employees").header("Authorization", bearer(tokenOf(hr)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only an ADMIN may grant the ADMIN role"));
    }

    @Test
    void hrCanCreateHrAndEmployeeAccounts() throws Exception {
        String hrToken = tokenOf(hr);
        NewEmployee helper = createEmployee(hrToken, "Helen Helper", Role.HR, null);
        NewEmployee employee = createEmployee(hrToken, "Eddie Employee", Role.EMPLOYEE, null);

        getAs(hrToken, "/api/employees/" + helper.id()).andExpect(jsonPath("$.role").value("HR"));
        getAs(hrToken, "/api/employees/" + employee.id()).andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void hrCannotGrantTheAdminRoleToAnExistingEmployee() throws Exception {
        mvc.perform(patch("/api/employees/" + staff.id()).header("Authorization", bearer(tokenOf(hr)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only an ADMIN may grant the ADMIN role"));
    }

    @Test
    void hrCannotEditOrTerminateAnAdmin() throws Exception {
        NewEmployee secondAdmin = createEmployee(admin, "Alice Admin", Role.ADMIN, null);
        String hrToken = tokenOf(hr);

        mvc.perform(patch("/api/employees/" + secondAdmin.id()).header("Authorization", bearer(hrToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("jobTitle", "Demoted"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Only an ADMIN may update an ADMIN account")));

        mvc.perform(patch("/api/employees/" + secondAdmin.id()).header("Authorization", bearer(hrToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("role", "EMPLOYEE"))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/employees/" + secondAdmin.id() + "/terminate")
                        .header("Authorization", bearer(hrToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrCannotChangeTheirOwnRole() throws Exception {
        mvc.perform(patch("/api/employees/" + hr.id()).header("Authorization", bearer(tokenOf(hr)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only an ADMIN may grant the ADMIN role"));

        mvc.perform(patch("/api/employees/" + hr.id()).header("Authorization", bearer(tokenOf(hr)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("role", "EMPLOYEE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot change your own role"));

        getAs(admin, "/api/employees/" + hr.id()).andExpect(jsonPath("$.role").value("HR"));
    }

    @Test
    void anAdminCannotChangeTheirOwnRoleEither() throws Exception {
        UUID adminId = UUID.fromString(body(getAs(admin, "/api/employees/me")).path("id").asText());
        mvc.perform(patch("/api/employees/" + adminId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("role", "EMPLOYEE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot change your own role"));
    }

    @Test
    void onlyAnAdminCanGrantAndRevokeTheAdminRole() throws Exception {
        mvc.perform(patch("/api/employees/" + hr.id()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // The promoted account now has admin powers of its own.
        String promoted = tokenOf(hr);
        createEmployee(promoted, "Another Admin", Role.ADMIN, null);

        mvc.perform(patch("/api/employees/" + hr.id()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("role", "HR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HR"));
    }

    @Test
    void aPlainEmployeeCannotEditAnybody() throws Exception {
        String staffToken = tokenOf(staff);
        mvc.perform(patch("/api/employees/" + staff.id()).header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("salary", new java.math.BigDecimal("99999.00")))))
                .andExpect(status().isForbidden());

        getAs(staffToken, "/api/employees/me").andExpect(jsonPath("$.salary").exists());
        assertThat(body(getAs(admin, "/api/employees/" + staff.id())).path("salary").decimalValue())
                .isEqualByComparingTo("5000.00");
    }
}
