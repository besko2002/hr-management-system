package com.example.hr;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The org hierarchy on a real 4-level tree with a sibling branch:
 *
 * <pre>
 *   Cora Ceo
 *     ├── Dirk Director
 *     │     └── Mary Manager
 *     │           ├── Stan Staff
 *     │           └── Sara Staff
 *     └── Diana Director (sibling branch)
 *           └── Mike Manager
 * </pre>
 */
class HierarchyIntegrationTest extends AbstractIntegrationTest {

    private String admin;
    private NewEmployee ceo;
    private NewEmployee director;
    private NewEmployee manager;
    private NewEmployee staff;
    private NewEmployee otherStaff;
    private NewEmployee siblingDirector;
    private NewEmployee siblingManager;

    @BeforeEach
    void buildTree() throws Exception {
        admin = adminToken();
        ceo = createEmployee(admin, "Cora Ceo", Role.EMPLOYEE, null, null, "20000.00");
        director = createEmployee(admin, "Dirk Director", Role.EMPLOYEE, ceo.id(), null, "15000.00");
        manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, director.id(), null, "10000.00");
        staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id(), null, "5000.00");
        otherStaff = createEmployee(admin, "Sara Staff", Role.EMPLOYEE, manager.id(), null, "5100.00");
        siblingDirector = createEmployee(admin, "Diana Director", Role.EMPLOYEE, ceo.id(), null, "14000.00");
        siblingManager = createEmployee(admin, "Mike Manager", Role.EMPLOYEE, siblingDirector.id(), null, "9000.00");
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions setManager(String token, UUID employeeId, UUID managerId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("managerId", managerId);
        return mvc.perform(put("/api/employees/" + employeeId + "/manager")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static JsonNode nodeNamed(JsonNode array, String fullName) {
        for (JsonNode node : array) {
            if (fullName.equals(node.path("fullName").asText())) {
                return node;
            }
        }
        throw new AssertionError("no node named " + fullName + " in " + array);
    }

    /** Names of the direct children only (findValuesAsText would recurse into grandchildren). */
    private static List<String> childNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (JsonNode child : node.path("children")) {
            names.add(child.path("fullName").asText());
        }
        return names;
    }

    private static int countNodes(JsonNode nodes) {
        int total = 0;
        for (JsonNode node : nodes) {
            total += 1 + countNodes(node.path("children"));
        }
        return total;
    }

    private static List<String> namesWithDepth(JsonNode team, int depth) {
        List<String> names = new ArrayList<>();
        for (JsonNode member : team) {
            if (member.path("depth").asInt() == depth) {
                names.add(member.path("fullName").asText());
            }
        }
        return names;
    }

    // ------------------------------------------------------------------ cycle prevention

    @Test
    void anEmployeeCannotBeTheirOwnManager() throws Exception {
        setManager(admin, manager.id(), manager.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An employee cannot be their own manager"));
    }

    @Test
    void aDirectCycleIsRefusedWithConflict() throws Exception {
        setManager(admin, director.id(), manager.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("would create a cycle")));
    }

    @Test
    void aDeepCycleThreeLevelsDownIsRefusedWithConflict() throws Exception {
        setManager(admin, ceo.id(), staff.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("would create a cycle")));

        // The tree is untouched after the refusal.
        getAs(admin, "/api/employees/" + ceo.id())
                .andExpect(jsonPath("$.managerId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void movingSomebodyAcrossBranchesIsAllowed() throws Exception {
        setManager(admin, staff.id(), siblingManager.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(siblingManager.id().toString()));

        JsonNode chain = body(getAs(admin, "/api/employees/" + staff.id() + "/chain"));
        assertThat(chain.findValuesAsText("fullName"))
                .containsExactly("Mike Manager", "Diana Director", "Cora Ceo");
    }

    @Test
    void aTerminatedEmployeeCannotBeMadeManager() throws Exception {
        mvc.perform(post("/api/employees/" + siblingManager.id() + "/terminate")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        setManager(admin, staff.id(), siblingManager.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A terminated employee cannot be a manager"));
    }

    @Test
    void theManagerCanBeClearedWhichMakesTheEmployeeARoot() throws Exception {
        setManager(admin, director.id(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(org.hamcrest.Matchers.nullValue()));

        JsonNode roots = body(getAs(admin, "/api/org-chart"));
        assertThat(roots.findValuesAsText("fullName")).contains("Dirk Director");
        assertThat(nodeNamed(roots, "Dirk Director").path("children").size()).isEqualTo(1);
    }

    @Test
    void anUnknownManagerIdIsNotFoundAndANonHrCallerIsForbidden() throws Exception {
        setManager(admin, staff.id(), UUID.randomUUID()).andExpect(status().isNotFound());

        String staffToken = login(staff.email(), staff.password());
        setManager(staffToken, otherStaff.id(), staff.id()).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ team queries

    @Test
    void myTeamReturnsOnlyTheDirectReports() throws Exception {
        String ceoToken = login(ceo.email(), ceo.password());
        getAs(ceoToken, "/api/employees/me/team")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fullName").value("Diana Director"))
                .andExpect(jsonPath("$[1].fullName").value("Dirk Director"))
                .andExpect(jsonPath("$[0].depth").value(1))
                .andExpect(jsonPath("$[0].salary").doesNotExist());

        String staffToken = login(staff.email(), staff.password());
        getAs(staffToken, "/api/employees/me/team")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void myWholeTeamReturnsEveryDescendantWithTheCorrectDepth() throws Exception {
        String ceoToken = login(ceo.email(), ceo.password());
        JsonNode team = body(getAs(ceoToken, "/api/employees/me/team/all").andExpect(status().isOk()));

        assertThat(team.size()).isEqualTo(6);
        assertThat(namesWithDepth(team, 1)).containsExactlyInAnyOrder("Dirk Director", "Diana Director");
        assertThat(namesWithDepth(team, 2)).containsExactlyInAnyOrder("Mary Manager", "Mike Manager");
        assertThat(namesWithDepth(team, 3)).containsExactlyInAnyOrder("Stan Staff", "Sara Staff");

        JsonNode directorTeam = body(getAs(login(director.email(), director.password()),
                "/api/employees/me/team/all"));
        assertThat(directorTeam.size()).isEqualTo(3);
        assertThat(namesWithDepth(directorTeam, 1)).containsExactly("Mary Manager");
        assertThat(namesWithDepth(directorTeam, 2)).containsExactlyInAnyOrder("Stan Staff", "Sara Staff");

        assertThat(body(getAs(login(staff.email(), staff.password()), "/api/employees/me/team/all")).size())
                .isZero();
    }

    @Test
    void teamResponsesNeverCarrySalaries() throws Exception {
        String ceoToken = login(ceo.email(), ceo.password());
        getAs(ceoToken, "/api/employees/me/team/all")
                .andExpect(jsonPath("$[0].salary").doesNotExist())
                .andExpect(jsonPath("$[5].salary").doesNotExist());
        assertThat(body(getAs(ceoToken, "/api/employees/me/team/all")).toString())
                .doesNotContain("salary");
        assertThat(body(getAs(ceoToken, "/api/employees/me/team")).toString()).doesNotContain("salary");
    }

    @Test
    void theManagementChainGoesUpwardsToTheRoot() throws Exception {
        JsonNode chain = body(getAs(admin, "/api/employees/" + staff.id() + "/chain")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].fullName").value("Mary Manager"))
                .andExpect(jsonPath("$[0].level").value(1))
                .andExpect(jsonPath("$[1].fullName").value("Dirk Director"))
                .andExpect(jsonPath("$[1].level").value(2))
                .andExpect(jsonPath("$[2].fullName").value("Cora Ceo"))
                .andExpect(jsonPath("$[2].level").value(3))
                .andExpect(jsonPath("$[0].salary").doesNotExist()));
        assertThat(chain.toString()).doesNotContain("salary");

        getAs(admin, "/api/employees/" + ceo.id() + "/chain")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anEmployeeCanReadTheirOwnChainButNotSomebodyElsesBranch() throws Exception {
        String staffToken = login(staff.email(), staff.password());
        getAs(staffToken, "/api/employees/" + staff.id() + "/chain").andExpect(status().isOk());
        getAs(staffToken, "/api/employees/" + siblingManager.id() + "/chain").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ org chart

    @Test
    void theOrgChartNestsTheWholeCompany() throws Exception {
        String staffToken = login(staff.email(), staff.password());
        JsonNode roots = body(getAs(staffToken, "/api/org-chart").andExpect(status().isOk()));

        // Roots are the employees without a manager: the bootstrap admin and the CEO.
        assertThat(roots.size()).isEqualTo(2);
        JsonNode ceoNode = nodeNamed(roots, "Cora Ceo");
        assertThat(ceoNode.path("jobTitle").asText()).isEqualTo("Cora Ceo title");
        assertThat(ceoNode.path("children").size()).isEqualTo(2);

        JsonNode directorNode = nodeNamed(ceoNode.path("children"), "Dirk Director");
        JsonNode managerNode = nodeNamed(directorNode.path("children"), "Mary Manager");
        assertThat(managerNode.path("children").size()).isEqualTo(2);
        assertThat(childNames(managerNode)).containsExactlyInAnyOrder("Stan Staff", "Sara Staff");
        assertThat(childNames(nodeNamed(ceoNode.path("children"), "Diana Director")))
                .containsExactly("Mike Manager");
        assertThat(childNames(ceoNode)).containsExactlyInAnyOrder("Dirk Director", "Diana Director");

        assertThat(countNodes(roots)).isEqualTo(8);
        assertThat(roots.toString()).doesNotContain("salary").doesNotContain("email");
    }

    @Test
    void theOrgChartShowsDepartmentsAndSkipsTerminatedEmployees() throws Exception {
        UUID department = createDepartment(admin, "Engineering");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/employees/" + manager.id()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("departmentId", department))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/employees/" + siblingDirector.id() + "/terminate")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        JsonNode roots = body(getAs(admin, "/api/org-chart"));
        JsonNode ceoNode = nodeNamed(roots, "Cora Ceo");
        assertThat(nodeNamed(nodeNamed(ceoNode.path("children"), "Dirk Director").path("children"),
                "Mary Manager").path("departmentName").asText()).isEqualTo("Engineering");

        // Diana is gone and her report was re-parented to the CEO.
        assertThat(roots.toString()).doesNotContain("Diana Director");
        assertThat(childNames(ceoNode)).containsExactlyInAnyOrder("Dirk Director", "Mike Manager");
        assertThat(countNodes(roots)).isEqualTo(7);
    }

    @Test
    void theOrgChartHandlesAThirtyPersonTreeInOneQuery() throws Exception {
        // Three more levels of breadth on top of the fixture tree: ~30 employees in total.
        List<UUID> parents = new ArrayList<>(List.of(staff.id(), otherStaff.id(), siblingManager.id()));
        List<UUID> nextLevel = new ArrayList<>();
        int created = 0;
        for (int level = 0; level < 2; level++) {
            int childrenPerParent = level == 0 ? 3 : 2;
            for (UUID parent : parents) {
                for (int i = 0; i < childrenPerParent; i++) {
                    nextLevel.add(createEmployee(admin, "Level" + level + " Person" + created++,
                            Role.EMPLOYEE, parent).id());
                }
            }
            parents = new ArrayList<>(nextLevel);
            nextLevel.clear();
        }

        long employees = jdbc.queryForObject("select count(*) from employees", Long.class);
        assertThat(employees).isEqualTo(8 + created);

        JsonNode roots = body(getAs(admin, "/api/org-chart").andExpect(status().isOk()));
        assertThat(countNodes(roots)).isEqualTo((int) employees);

        // The deepest branch is still nested correctly: ceo -> director -> manager -> staff -> L0 -> L1.
        JsonNode node = nodeNamed(roots, "Cora Ceo");
        for (String name : List.of("Dirk Director", "Mary Manager", "Stan Staff")) {
            node = nodeNamed(node.path("children"), name);
        }
        assertThat(node.path("children").size()).isEqualTo(3);
        assertThat(node.path("children").get(0).path("children").size()).isEqualTo(2);
    }

    @Test
    void theOrgChartRequiresAuthentication() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/org-chart"))
                .andExpect(status().isUnauthorized());
    }
}
