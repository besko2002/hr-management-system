package com.example.hr.audit;

import com.example.hr.AbstractIntegrationTest;
import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditIntegrationTest extends AbstractIntegrationTest {

    @Test
    void bootstrapAdminRevisionIsAttributedToSystem() {
        UUID adminId = jdbc.queryForObject(
                "select id from employees where lower(email) = lower(?)", UUID.class, ADMIN_EMAIL);
        String actor = jdbc.queryForObject("""
                select r.changed_by
                  from employees_aud a
                  join revinfo r on r.rev = a.rev
                 where a.id = ? and a.revtype = 0
                 order by a.rev
                 limit 1
                """, String.class, adminId);
        assertThat(actor).isEqualTo(AuditRevisionListener.SYSTEM);
    }

    @Test
    void salaryManagerAndStatusChangesAreRecordedWithActorAndBeforeAfter() throws Exception {
        String admin = adminToken();
        NewEmployee hr = createEmployee(admin, "Helen Hr", Role.HR, null);
        String hrToken = login(hr.email(), hr.password());

        NewEmployee manager = createEmployee(hrToken, "Mary Manager", Role.EMPLOYEE, null, null, "9000.00");
        NewEmployee staff = createEmployee(hrToken, "Stan Staff", Role.EMPLOYEE, manager.id(), null, "5000.00");

        mvc.perform(patch("/api/employees/" + staff.id()).header("Authorization", bearer(hrToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("salary", "6500.00"))))
                .andExpect(status().isOk());

        mvc.perform(put("/api/employees/" + staff.id() + "/manager")
                        .header("Authorization", bearer(hrToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("managerId", hr.id()))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/employees/" + staff.id() + "/terminate")
                        .header("Authorization", bearer(hrToken)))
                .andExpect(status().isOk());

        JsonNode page = body(getAs(hrToken, "/api/audit/employees/" + staff.id() + "?size=100")
                .andExpect(status().isOk()));
        List<JsonNode> changes = asList(page.path("content"));

        assertThat(findChange(changes, "salary", "5000.00", "6500.00").path("changedBy").asText())
                .isEqualTo(hr.email());
        assertThat(findChange(changes, "managerId", manager.id().toString(), hr.id().toString())
                .path("changedBy").asText()).isEqualTo(hr.email());
        assertThat(findChange(changes, "status", "ACTIVE", "TERMINATED").path("changedBy").asText())
                .isEqualTo(hr.email());
    }

    @Test
    void terminatingAnEmployeeAuditsCascadedManagerMovesOnReports() throws Exception {
        String admin = adminToken();
        NewEmployee boss = createEmployee(admin, "Boss Person", Role.EMPLOYEE, null);
        NewEmployee middle = createEmployee(admin, "Middle Manager", Role.EMPLOYEE, boss.id());
        NewEmployee report = createEmployee(admin, "Direct Report", Role.EMPLOYEE, middle.id());

        mvc.perform(post("/api/employees/" + middle.id() + "/terminate")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        JsonNode page = body(getAs(admin, "/api/audit/employees/" + report.id() + "?size=100")
                .andExpect(status().isOk()));
        findChange(asList(page.path("content")), "managerId", middle.id().toString(), boss.id().toString());
    }

    @Test
    void auditAccessIsHrAdminOnlyEveryoneElseGets404() throws Exception {
        String admin = adminToken();
        NewEmployee hr = createEmployee(admin, "Rita Hr", Role.HR, null);
        NewEmployee manager = createEmployee(admin, "Manny Manager", Role.EMPLOYEE, null);
        NewEmployee staff = createEmployee(admin, "Eddie Employee", Role.EMPLOYEE, manager.id());

        String path = "/api/audit/employees/" + staff.id();
        getAs(admin, path).andExpect(status().isOk());
        getAs(login(hr.email(), hr.password()), path).andExpect(status().isOk());
        getAs(login(manager.email(), manager.password()), path).andExpect(status().isNotFound());
        getAs(login(staff.email(), staff.password()), path).andExpect(status().isNotFound());
        getAs(admin, "/api/audit/employees/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    private static List<JsonNode> asList(JsonNode array) {
        List<JsonNode> list = new ArrayList<>();
        array.forEach(list::add);
        return list;
    }

    private static JsonNode findChange(List<JsonNode> changes, String field, String before, String after) {
        for (JsonNode change : changes) {
            if (field.equals(change.path("field").asText())
                    && equalNullable(before, change.path("before"))
                    && equalNullable(after, change.path("after"))) {
                return change;
            }
        }
        throw new AssertionError("No change for field=" + field + " before=" + before + " after=" + after
                + " in " + changes);
    }

    private static boolean equalNullable(String expected, JsonNode actual) {
        if (expected == null) {
            return actual.isNull();
        }
        return expected.equals(actual.asText());
    }
}
