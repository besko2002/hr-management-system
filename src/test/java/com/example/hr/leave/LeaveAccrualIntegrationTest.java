package com.example.hr.leave;

import com.example.hr.employee.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The accrual under the <em>default</em> configuration ({@code app.leave.annual.upfront=true}).
 *
 * <p>Upfront granting and monthly accrual are alternatives: the whole (pro-rated) year is
 * already in {@code entitled}, so a run must refuse instead of adding on top. The
 * behaviour of the accrual itself lives in {@link LeaveAccrualMonthlyIntegrationTest},
 * which runs with {@code upfront=false}.
 */
class LeaveAccrualIntegrationTest extends AbstractLeaveIntegrationTest {

    private static final String UPFRONT_MESSAGE =
            "Monthly accrual is disabled while app.leave.annual.upfront=true; ANNUAL is granted upfront";

    private String admin;
    private String staffToken;
    private String hrToken;
    private int year;
    private int month;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        NewEmployee manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, null);
        NewEmployee staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        NewEmployee hr = createEmployee(admin, "Hilda Hr", Role.HR, null);
        staffToken = login(staff.email(), staff.password());
        hrToken = login(hr.email(), hr.password());

        year = LocalDate.now().getYear();
        month = LocalDate.now().getMonthValue();
    }

    private long accrualRows() {
        Long count = jdbc.queryForObject("select count(*) from accrual_log", Long.class);
        return count == null ? 0 : count;
    }

    @Test
    void theAccrualIsRefusedWhileTheEntitlementIsGrantedUpfront() throws Exception {
        runAccrual(admin, year, month)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(UPFRONT_MESSAGE));
    }

    @Test
    void theRefusedRunLeavesEveryBalanceUntouched() throws Exception {
        JsonNode before = balance(staffToken, LeaveType.ANNUAL, year);
        assertThat(before.path("entitledDays").asInt()).isEqualTo(21);

        runAccrual(admin, year, month).andExpect(status().isConflict());

        JsonNode after = balance(staffToken, LeaveType.ANNUAL, year);
        assertThat(after.path("entitledDays").asInt()).isEqualTo(21);
        assertThat(after.path("remainingDays").asInt()).isEqualTo(21);
    }

    @Test
    void theRefusalHappensBeforeAnyRowIsClaimed() throws Exception {
        runAccrual(admin, year, month).andExpect(status().isConflict());
        runAccrual(admin, year, month == 12 ? 1 : month + 1).andExpect(status().isConflict());
        assertThat(accrualRows()).isZero();
    }

    @Test
    void theRunWithoutParametersIsRefusedTheSameWay() throws Exception {
        mvc.perform(post("/api/leave/accrual/run").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(UPFRONT_MESSAGE));
    }

    @Test
    void theAuthorityCheckStillComesFirst() throws Exception {
        runAccrual(staffToken, year, month).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only HR or an ADMIN may run the leave accrual"));
        // HR passes the authority check and is then refused by the configuration guard.
        runAccrual(hrToken, year, month).andExpect(status().isConflict());
    }

    @Test
    void anUnauthenticatedCallerCannotRunTheAccrual() throws Exception {
        mvc.perform(post("/api/leave/accrual/run")).andExpect(status().isUnauthorized());
    }
}
