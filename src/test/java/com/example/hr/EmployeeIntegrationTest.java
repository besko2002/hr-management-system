package com.example.hr;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Employee creation, updates, termination and the HR list (pagination, filters, sort). */
class EmployeeIntegrationTest extends AbstractIntegrationTest {

    // ------------------------------------------------------------------ helpers

    private ResultActions createRaw(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/employees").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static Map<String, Object> payload(String fullName, String email, Role role) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", fullName);
        body.put("email", email);
        body.put("role", role.name());
        body.put("jobTitle", "Tester");
        body.put("hireDate", "2024-03-01");
        body.put("salary", new BigDecimal("4200.00"));
        return body;
    }

    // ------------------------------------------------------------------ create

    @Test
    void creatingAnEmployeeReturnsTheTemporaryPasswordExactlyOnce() throws Exception {
        String admin = adminToken();
        JsonNode created = body(createRaw(admin, payload("Nina New", "nina.new@hr.local", Role.EMPLOYEE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.employee.employeeNumber").value(
                        org.hamcrest.Matchers.matchesPattern("EMP-\\d{4,}")))
                .andExpect(jsonPath("$.employee.mustChangePassword").value(true)));

        UUID id = UUID.fromString(created.path("employee").path("id").asText());
        String temporaryPassword = created.path("temporaryPassword").asText();
        assertThat(temporaryPassword).isNotBlank();

        // The password is never retrievable again.
        getAs(admin, "/api/employees/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // ... but it works for the first login, and the flag is stored in the database.
        String token = login("nina.new@hr.local", temporaryPassword);
        getAs(token, "/api/employees/me").andExpect(jsonPath("$.mustChangePassword").value(true));
        Boolean flag = jdbc.queryForObject(
                "select must_change_password from employees where id = ?", Boolean.class, id);
        assertThat(flag).isTrue();
    }

    @Test
    void theForcedPasswordChangeClearsTheFlagAndTheNewPasswordWorks() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Fiona First", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", staff.password(), "newPassword", "FirstLogin@2024"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        login(staff.email(), "FirstLogin@2024");
        Boolean flag = jdbc.queryForObject(
                "select must_change_password from employees where id = ?", Boolean.class, staff.id());
        assertThat(flag).isFalse();
    }

    @Test
    void emailsAreStoredLowerCasedAndDuplicatesAreRefusedWithConflict() throws Exception {
        String admin = adminToken();
        createRaw(admin, payload("Dana Dup", "Dana.Dup@HR.local", Role.EMPLOYEE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.email").value("dana.dup@hr.local"));

        createRaw(admin, payload("Dana Twin", "dana.dup@hr.local", Role.EMPLOYEE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An employee with this email already exists"));

        // Case-insensitively duplicate too.
        createRaw(admin, payload("Dana Shout", "DANA.DUP@hr.local", Role.EMPLOYEE))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingAnEmployeeValidatesThePayloadWithFieldErrors() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "  ");
        body.put("email", "not-an-email");
        body.put("salary", new BigDecimal("-5"));

        createRaw(adminToken(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.fullName").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.role").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.hireDate").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.salary").isNotEmpty());
    }

    @Test
    void creatingAnEmployeeWithAnUnknownDepartmentIsNotFound() throws Exception {
        Map<String, Object> body = payload("Gina Ghost", "gina.ghost@hr.local", Role.EMPLOYEE);
        body.put("departmentId", UUID.randomUUID());
        createRaw(adminToken(), body).andExpect(status().isNotFound());
    }

    @Test
    void aPlainEmployeeCannotCreateEmployees() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Peter Plain", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        createRaw(token, payload("Illegal Hire", "illegal@hr.local", Role.EMPLOYEE))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ update

    @Test
    void putReplacesTheMutableProfile() throws Exception {
        String admin = adminToken();
        UUID department = createDepartment(admin, "Engineering");
        NewEmployee staff = createEmployee(admin, "Uma Update", Role.EMPLOYEE, null);

        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Uma Updated");
        body.put("email", "uma.updated@hr.local");
        body.put("role", "HR");
        body.put("jobTitle", "Recruiter");
        body.put("departmentId", department);
        body.put("hireDate", "2023-06-30");
        body.put("salary", new BigDecimal("8100.50"));

        JsonNode updated = body(mvc.perform(put("/api/employees/" + staff.id())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Uma Updated"))
                .andExpect(jsonPath("$.email").value("uma.updated@hr.local"))
                .andExpect(jsonPath("$.role").value("HR"))
                .andExpect(jsonPath("$.jobTitle").value("Recruiter"))
                .andExpect(jsonPath("$.departmentName").value("Engineering"))
                .andExpect(jsonPath("$.hireDate").value("2023-06-30")));
        assertThat(updated.path("salary").decimalValue()).isEqualByComparingTo("8100.50");
    }

    @Test
    void putClearsFieldsThatAreOmitted() throws Exception {
        String admin = adminToken();
        UUID department = createDepartment(admin, "Ops");
        NewEmployee staff = createEmployee(admin, "Clara Clear", Role.EMPLOYEE, null, department, "1000.00");

        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Clara Clear");
        body.put("email", staff.email());
        body.put("role", "EMPLOYEE");
        body.put("hireDate", "2024-01-15");

        mvc.perform(put("/api/employees/" + staff.id()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.salary").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.jobTitle").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void patchOnlyTouchesTheFieldsItIsGiven() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Paula Patch", Role.EMPLOYEE, null);

        JsonNode patched = body(mvc.perform(patch("/api/employees/" + staff.id())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "jobTitle", "Senior Tester", "salary", new BigDecimal("7777.77")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Senior Tester"))
                .andExpect(jsonPath("$.fullName").value("Paula Patch"))
                .andExpect(jsonPath("$.email").value(staff.email()))
                .andExpect(jsonPath("$.role").value("EMPLOYEE")));
        assertThat(patched.path("salary").decimalValue()).isEqualByComparingTo("7777.77");
    }

    @Test
    void patchRefusesAnEmailThatBelongsToSomebodyElse() throws Exception {
        String admin = adminToken();
        NewEmployee first = createEmployee(admin, "Ann One", Role.EMPLOYEE, null);
        NewEmployee second = createEmployee(admin, "Bob Two", Role.EMPLOYEE, null);

        mvc.perform(patch("/api/employees/" + second.id()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", first.email()))))
                .andExpect(status().isConflict());
    }

    @Test
    void updatingAnUnknownEmployeeIsNotFound() throws Exception {
        mvc.perform(patch("/api/employees/" + UUID.randomUUID()).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("jobTitle", "Ghost"))))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ terminate

    @Test
    void terminationSetsTheStatusAndBlocksLoginAndExistingTokens() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Ted Terminate", Role.EMPLOYEE, null);
        String staffToken = login(staff.email(), staff.password());
        getAs(staffToken, "/api/employees/me").andExpect(status().isOk());

        mvc.perform(post("/api/employees/" + staff.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));

        getAs(staffToken, "/api/employees/me").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", staff.email(), "password", staff.password()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void terminationReParentsDirectReportsToTheTerminatedPersonsManager() throws Exception {
        String admin = adminToken();
        NewEmployee ceo = createEmployee(admin, "Cora Ceo", Role.EMPLOYEE, null);
        NewEmployee director = createEmployee(admin, "Dirk Director", Role.EMPLOYEE, ceo.id());
        NewEmployee reportA = createEmployee(admin, "Rita Report", Role.EMPLOYEE, director.id());
        NewEmployee reportB = createEmployee(admin, "Rolf Report", Role.EMPLOYEE, director.id());

        mvc.perform(post("/api/employees/" + director.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        for (NewEmployee report : List.of(reportA, reportB)) {
            getAs(admin, "/api/employees/" + report.id())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.managerId").value(ceo.id().toString()))
                    .andExpect(jsonPath("$.managerName").value("Cora Ceo"));
        }
        UUID storedManager = jdbc.queryForObject(
                "select manager_id from employees where id = ?", UUID.class, reportA.id());
        assertThat(storedManager).isEqualTo(ceo.id());
    }

    @Test
    void reportsOfATerminatedRootBecomeRootsThemselves() throws Exception {
        String admin = adminToken();
        NewEmployee root = createEmployee(admin, "Rosa Root", Role.EMPLOYEE, null);
        NewEmployee report = createEmployee(admin, "Rudi Report", Role.EMPLOYEE, root.id());

        mvc.perform(post("/api/employees/" + root.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        getAs(admin, "/api/employees/" + report.id())
                .andExpect(jsonPath("$.managerId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void terminatingTwiceOrTerminatingYourselfIsRefused() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Twice Terminated", Role.EMPLOYEE, null);
        mvc.perform(post("/api/employees/" + staff.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/employees/" + staff.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This employee is already terminated"));

        UUID adminId = UUID.fromString(body(getAs(admin, "/api/employees/me")).path("id").asText());
        mvc.perform(post("/api/employees/" + adminId + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You cannot terminate your own account"));
    }

    @Test
    void aPlainEmployeeCannotTerminateAnybody() throws Exception {
        String admin = adminToken();
        NewEmployee victim = createEmployee(admin, "Vera Victim", Role.EMPLOYEE, null);
        NewEmployee staff = createEmployee(admin, "Sven Staff", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        mvc.perform(post("/api/employees/" + victim.id() + "/terminate").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ list

    @Test
    void theListIsPaginatedAndTheSizeIsCappedAtOneHundred() throws Exception {
        String admin = adminToken();
        for (int i = 0; i < 5; i++) {
            createEmployee(admin, "Page Person " + i, Role.EMPLOYEE, null);
        }

        getAs(admin, "/api/employees?page=0&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3));

        getAs(admin, "/api/employees?page=2&size=2")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(2));

        getAs(admin, "/api/employees?size=5000")
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(6));
    }

    @Test
    void theListSearchesNameEmailAndNumberCaseInsensitively() throws Exception {
        String admin = adminToken();
        NewEmployee searchable = createEmployee(admin, "Zoe Searchable", Role.EMPLOYEE, null);
        createEmployee(admin, "Other Person", Role.EMPLOYEE, null);

        getAs(admin, "/api/employees?q=sEaRcH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Zoe Searchable"));

        getAs(admin, "/api/employees?q=" + searchable.email().substring(0, 6))
                .andExpect(jsonPath("$.totalElements").value(1));

        String number = body(getAs(admin, "/api/employees/" + searchable.id())).path("employeeNumber").asText();
        getAs(admin, "/api/employees?q=" + number.toLowerCase(java.util.Locale.ROOT))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeNumber").value(number));

        getAs(admin, "/api/employees?q=nobody-matches-this")
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void theListFiltersByDepartmentAndStatus() throws Exception {
        String admin = adminToken();
        UUID engineering = createDepartment(admin, "Engineering");
        UUID sales = createDepartment(admin, "Sales");
        createEmployee(admin, "Eve Engineer", Role.EMPLOYEE, null, engineering, "5000.00");
        createEmployee(admin, "Sally Sales", Role.EMPLOYEE, null, sales, "4000.00");
        NewEmployee leaver = createEmployee(admin, "Leo Leaver", Role.EMPLOYEE, null, engineering, "4500.00");

        getAs(admin, "/api/employees?departmentId=" + engineering)
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(post("/api/employees/" + leaver.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        getAs(admin, "/api/employees?status=TERMINATED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Leo Leaver"));
        getAs(admin, "/api/employees?status=ACTIVE&departmentId=" + engineering)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Eve Engineer"));
        getAs(admin, "/api/employees?status=BOGUS").andExpect(status().isBadRequest());
    }

    @Test
    void theListCanBeSortedAndRefusesUnknownSortFields() throws Exception {
        String admin = adminToken();
        UUID department = createDepartment(admin, "Sortland");
        createEmployee(admin, "Aaron Alpha", Role.EMPLOYEE, null, department, "1000.00");
        createEmployee(admin, "Zara Omega", Role.EMPLOYEE, null, department, "9000.00");

        getAs(admin, "/api/employees?sort=fullName,asc")
                .andExpect(jsonPath("$.content[0].fullName").value("Aaron Alpha"));
        getAs(admin, "/api/employees?sort=fullName,desc")
                .andExpect(jsonPath("$.content[0].fullName").value("Zara Omega"));
        // Filtered to the department so the salary-less bootstrap admin does not interfere.
        getAs(admin, "/api/employees?sort=salary,desc&departmentId=" + department)
                .andExpect(jsonPath("$.content[0].fullName").value("Zara Omega"));
        getAs(admin, "/api/employees?sort=salary,asc&departmentId=" + department)
                .andExpect(jsonPath("$.content[0].fullName").value("Aaron Alpha"));
        getAs(admin, "/api/employees?sort=passwordHash,asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Cannot sort by 'passwordHash'")));
    }

    @Test
    void theListIsHrOrAdminOnly() throws Exception {
        String admin = adminToken();
        NewEmployee hr = createEmployee(admin, "Hugo Hr", Role.HR, null);
        NewEmployee staff = createEmployee(admin, "Nora Nosy", Role.EMPLOYEE, null);

        getAs(login(hr.email(), hr.password()), "/api/employees").andExpect(status().isOk());
        getAs(login(staff.email(), staff.password()), "/api/employees")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("list employees")));
    }

    @Test
    void theListAlwaysCarriesSalariesBecauseItIsHrOnly() throws Exception {
        String admin = adminToken();
        createEmployee(admin, "Sara Salary", Role.EMPLOYEE, null, null, "1234.56");

        JsonNode page = body(getAs(admin, "/api/employees?q=Sara").andExpect(status().isOk()));
        assertThat(page.path("content").get(0).path("salary").decimalValue()).isEqualByComparingTo("1234.56");
    }

    @Test
    void meReturnsTheCallersOwnProfileIncludingSalary() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Mia Me", Role.EMPLOYEE, null, null, "3333.00");
        String token = login(staff.email(), staff.password());

        JsonNode me = body(getAs(token, "/api/employees/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(staff.id().toString()))
                .andExpect(jsonPath("$.email").value(staff.email())));
        assertThat(me.path("salary").decimalValue()).isEqualByComparingTo("3333.00");
    }

    @Test
    void unknownPathsAndWrongMethodsReturnJsonErrors() throws Exception {
        String admin = adminToken();
        getAs(admin, "/api/does-not-exist")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No such endpoint"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/employees/me").header("Authorization", bearer(admin)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        getAs(admin, "/api/employees/not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid value")));
    }
}
