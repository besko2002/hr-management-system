package com.example.hr;

import com.example.hr.auth.AdminBootstrap;
import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for the API tests: one real PostgreSQL container for the whole JVM, a clean
 * schema before every test, and the seeded bootstrap admin restored each time.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("hr").withUsername("hr").withPassword("hr");

    static {
        POSTGRES.start();
    }

    protected static final String ADMIN_EMAIL = "admin@hr.local";
    protected static final String ADMIN_PASSWORD = "Admin@12345";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected AdminBootstrap adminBootstrap;

    @BeforeEach
    void resetDatabase() {
        // `cascade` also empties everything that references employees (leave requests,
        // balances, the accrual log); `holidays` has no such reference, hence the explicit
        // mention. `leave_types` is seeded reference data and is deliberately preserved.
        // Envers tables have no FK to employees, so they are truncated explicitly.
        jdbc.execute("""
                truncate table employees_aud, leave_requests_aud, payroll_runs_aud, revinfo,
                               employees, departments, holidays cascade
                """);
        adminBootstrap.run(null);
    }

    // ------------------------------------------------------------------ fixtures

    /** A freshly created account plus the one-time password needed to log in as them. */
    public record NewEmployee(UUID id, String fullName, String email, String password) {
    }

    protected String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("accessToken").asText();
    }

    protected String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected NewEmployee createEmployee(String token, String fullName, Role role, UUID managerId) throws Exception {
        return createEmployee(token, fullName, role, managerId, null, "5000.00");
    }

    protected NewEmployee createEmployee(String token, String fullName, Role role, UUID managerId,
                                         UUID departmentId, String salary) throws Exception {
        return createEmployee(token, fullName, role, managerId, departmentId, salary, "2024-01-15");
    }

    /** Same, with an explicit hire date — the leave tests need it for balance pro-rating. */
    protected NewEmployee createEmployee(String token, String fullName, Role role, UUID managerId,
                                         UUID departmentId, String salary, String hireDate) throws Exception {
        Map<String, Object> body = new HashMap<>();
        String email = slug(fullName) + "-" + UUID.randomUUID().toString().substring(0, 8) + "@hr.local";
        body.put("fullName", fullName);
        body.put("email", email);
        body.put("role", role.name());
        body.put("jobTitle", fullName + " title");
        body.put("hireDate", hireDate);
        body.put("salary", salary == null ? null : new java.math.BigDecimal(salary));
        body.put("managerId", managerId);
        body.put("departmentId", departmentId);

        String response = mvc.perform(post("/api/employees").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return new NewEmployee(UUID.fromString(node.path("employee").path("id").asText()), fullName, email,
                node.path("temporaryPassword").asText());
    }

    protected UUID createDepartment(String token, String name) throws Exception {
        String response = mvc.perform(post("/api/departments").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).path("id").asText());
    }

    protected ResultActions getAs(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(token)));
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String slug(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", ".");
    }
}
