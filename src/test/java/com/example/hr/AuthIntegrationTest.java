package com.example.hr;

import com.example.hr.employee.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, token handling, the bootstrap admin and the change-password flow. */
class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String GENERIC = "Invalid email or password";

    @Test
    void theBootstrapAdminCanLogIn() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(8 * 3600))
                .andExpect(jsonPath("$.user.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
    }

    @Test
    void onlyOneBootstrapAdminIsSeededEvenWhenTheRunnerRunsAgain() throws Exception {
        adminBootstrap.run(null);
        adminBootstrap.run(null);
        Integer admins = jdbc.queryForObject("select count(*) from employees where role = 'ADMIN'", Integer.class);
        assertThat(admins).isEqualTo(1);
    }

    @Test
    void loginIsCaseInsensitiveInTheEmail() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "ADMIN@HR.LOCAL", "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void aWrongPasswordIsRejectedWithTheGenericMessage() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", ADMIN_EMAIL, "password", "nope"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(GENERIC))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void anUnknownEmailIsRejectedWithTheSameGenericMessage() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "ghost@hr.local", "password", "whatever"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(GENERIC));
    }

    @Test
    void aTerminatedEmployeeCannotLogInAndGetsTheSameGenericMessage() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Tina Terminated", Role.EMPLOYEE, null);
        login(staff.email(), staff.password());

        mvc.perform(post("/api/employees/" + staff.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", staff.email(), "password", staff.password()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(GENERIC));
    }

    @Test
    void anAlreadyIssuedTokenStopsWorkingAfterTermination() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Tom Token", Role.EMPLOYEE, null);
        String staffToken = login(staff.email(), staff.password());
        getAs(staffToken, "/api/employees/me").andExpect(status().isOk());

        mvc.perform(post("/api/employees/" + staff.id() + "/terminate").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        getAs(staffToken, "/api/employees/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This account is no longer active"));
    }

    @Test
    void thereIsNoPublicRegistrationEndpoint() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "x@hr.local", "password", "Password1!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMissingTokenYieldsJsonUnauthorized() throws Exception {
        mvc.perform(get("/api/employees/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/employees/me"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
    }

    @Test
    void aGarbageTokenYieldsJsonUnauthorized() throws Exception {
        mvc.perform(get("/api/employees/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginValidatesItsPayload() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "not-an-email", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    void aNewEmployeeMustChangeTheirPasswordAndTheFlagClearsAfterwards() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Carla Change", Role.EMPLOYEE, null);
        requirePasswordChange(staff.id());
        String token = login(staff.email(), staff.password());

        getAs(token, "/api/employees/me").andExpect(jsonPath("$.mustChangePassword").value(true));

        String fresh = mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", staff.password(), "newPassword", "BrandNew@2024"))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Access-Token"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn().getResponse().getHeader("X-Access-Token");

        getAs(fresh, "/api/employees/me").andExpect(jsonPath("$.mustChangePassword").value(false));
        getAs(fresh, "/api/leave/balances/me").andExpect(status().isOk());
        login(staff.email(), "BrandNew@2024");
    }

    @Test
    void whileAPasswordChangeIsRequiredOnlyChangePasswordAndMeAreAllowed() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Gina Gate", Role.HR, null);
        requirePasswordChange(staff.id());
        String token = login(staff.email(), staff.password());

        getAs(token, "/api/employees/me").andExpect(status().isOk());
        getAs(token, "/api/employees")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You must change your password before using this resource"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        getAs(token, "/api/leave/balances/me").andExpect(status().isForbidden());
        getAs(token, "/api/departments").andExpect(status().isForbidden());
        mvc.perform(post("/api/departments").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Blocked"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void theSeededAdminMustChangeTheirPassword() throws Exception {
        jdbc.execute("truncate table employees_aud, revinfo, employees cascade");
        adminBootstrap.run(null);
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        getAs(token, "/api/employees/me").andExpect(jsonPath("$.mustChangePassword").value(true));
        getAs(token, "/api/employees").andExpect(status().isForbidden());
    }

    @Test
    void tokensIssuedBeforeAPasswordChangeAreRejected() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Paul Rotate", Role.EMPLOYEE, null);
        String old = login(staff.email(), staff.password());
        // iat has one-second resolution: make sure the change happens in a later second.
        Thread.sleep(1100);

        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(old))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", staff.password(), "newPassword", "Rotated@2025"))))
                .andExpect(status().isOk());

        getAs(old, "/api/employees/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This token was issued before the last password change"));
        getAs(login(staff.email(), "Rotated@2025"), "/api/employees/me").andExpect(status().isOk());
    }

    @Test
    void repeatedLoginFailuresAreThrottledPerEmailAndIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginFrom("10.0.0.1", ADMIN_EMAIL, "wrong-" + i).andExpect(status().isUnauthorized());
        }
        // Even the right password is refused while blocked.
        loginFrom("10.0.0.1", ADMIN_EMAIL, ADMIN_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
        // Another client IP, or another e-mail from the same IP, is not affected.
        loginFrom("10.0.0.2", ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
        loginFrom("10.0.0.1", "ghost@hr.local", "x").andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailsCountTowardsTheThrottleToo() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginFrom("10.0.0.3", "ghost@hr.local", "x").andExpect(status().isUnauthorized());
        }
        loginFrom("10.0.0.3", "ghost@hr.local", "x").andExpect(status().isTooManyRequests());
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            loginFrom("10.0.0.4", ADMIN_EMAIL, "wrong").andExpect(status().isUnauthorized());
        }
        loginFrom("10.0.0.4", ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            loginFrom("10.0.0.4", ADMIN_EMAIL, "wrong").andExpect(status().isUnauthorized());
        }
        loginFrom("10.0.0.4", ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
    }

    private ResultActions loginFrom(String ip, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    @Test
    void theOldPasswordNoLongerWorksAfterAChange() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Olga Old", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", staff.password(), "newPassword", "Rotated@2024"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", staff.email(), "password", staff.password()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRejectsAWrongCurrentPassword() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Wanda Wrong", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", "definitely-wrong", "newPassword", "Another@2024"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The current password is incorrect"));
    }

    @Test
    void changePasswordRejectsAShortNewPassword() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Shorty Short", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", staff.password(), "newPassword", "short1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").isNotEmpty());
    }

    @Test
    void changePasswordRejectsReusingTheSamePassword() throws Exception {
        String admin = adminToken();
        NewEmployee staff = createEmployee(admin, "Rita Repeat", Role.EMPLOYEE, null);
        String token = login(staff.email(), staff.password());

        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", staff.password(), "newPassword", staff.password()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The new password must differ from the current one"));
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", "a", "newPassword", "Password@1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublicAndTheApiDocsAreOffByDefault() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
