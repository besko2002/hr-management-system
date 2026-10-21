package com.example.hr;

import com.example.hr.employee.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Department CRUD and its guard rails. */
class DepartmentIntegrationTest extends AbstractIntegrationTest {

    @Test
    void hrCanCreateADepartment() throws Exception {
        String admin = adminToken();
        NewEmployee hr = createEmployee(admin, "Hana HR", Role.HR, null);
        String hrToken = login(hr.email(), hr.password());

        mvc.perform(post("/api/departments").header("Authorization", bearer(hrToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Engineering"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Engineering"));
    }

    @Test
    void aPlainEmployeeCannotCreateADepartment() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Sam Staff", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        mvc.perform(post("/api/departments").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Shadow IT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void duplicateDepartmentNamesAreRefusedWithConflict() throws Exception {
        String admin = adminToken();
        createDepartment(admin, "Finance");

        mvc.perform(post("/api/departments").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "finance"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void departmentNameIsValidated() throws Exception {
        mvc.perform(post("/api/departments").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
    }

    @Test
    void aDepartmentCanBeRenamed() throws Exception {
        String admin = adminToken();
        UUID id = createDepartment(admin, "Ops");

        mvc.perform(put("/api/departments/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Operations"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Operations"));
    }

    @Test
    void renamingAnUnknownDepartmentIsNotFound() throws Exception {
        mvc.perform(put("/api/departments/" + UUID.randomUUID()).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Ghost"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anyAuthenticatedUserCanListDepartments() throws Exception {
        String admin = adminToken();
        createDepartment(admin, "Legal");
        createDepartment(admin, "Design");
        NewEmployee staff = createEmployee(admin, "Lena List", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        getAs(token, "/api/departments")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Design"))
                .andExpect(jsonPath("$[1].name").value("Legal"));
    }

    @Test
    void anEmptyDepartmentCanBeDeleted() throws Exception {
        String admin = adminToken();
        UUID id = createDepartment(admin, "Temporary");

        mvc.perform(delete("/api/departments/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        getAs(admin, "/api/departments").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingADepartmentWithEmployeesIsRefusedWithConflict() throws Exception {
        String admin = adminToken();
        UUID id = createDepartment(admin, "Support");
        createEmployee(admin, "Dana Desk", Role.EMPLOYEE, null, id, "3000.00");

        mvc.perform(delete("/api/departments/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("still has 1 employee")));
    }

    @Test
    void aPlainEmployeeCannotDeleteADepartment() throws Exception {
        String admin = adminToken();
        UUID id = createDepartment(admin, "Marketing");
        NewEmployee staff = createEmployee(admin, "Nick Nope", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        mvc.perform(delete("/api/departments/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }
}
