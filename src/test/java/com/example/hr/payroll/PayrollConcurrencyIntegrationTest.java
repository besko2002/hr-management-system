package com.example.hr.payroll;

import com.example.hr.employee.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real race: two HR users posting the same month at the same instant. The
 * {@code (run_year, run_month)} unique constraint — not the {@code exists} pre-check — is
 * what makes the outcome safe, so exactly one run and one set of payslips survive and the
 * loser's whole transaction is rolled back.
 */
class PayrollConcurrencyIntegrationTest extends AbstractPayrollIntegrationTest {

    private String hrToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        NewEmployee hr = createEmployee(adminToken, "Hana HR", Role.HR, null);
        hrToken = login(hr.email(), hr.password());
        createEmployee(adminToken, "Sara Staff", Role.EMPLOYEE, null, null, REFERENCE_SALARY);
        createEmployee(adminToken, "Omar Other", Role.EMPLOYEE, null, null, "6000.00");
    }

    private <T> List<T> inParallel(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoSimultaneousRunsForTheSameMonthLeaveExactlyOneRun() throws Exception {
        List<Integer> statuses = inParallel(List.of(
                () -> createRun(hrToken, MONTH_YEAR, MONTH).andReturn().getResponse().getStatus(),
                () -> createRun(adminToken, MONTH_YEAR, MONTH).andReturn().getResponse().getStatus()));

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(1);

        Long runs = jdbc.queryForObject("select count(*) from payroll_runs", Long.class);
        assertThat(runs).isEqualTo(1L);
    }

    @Test
    void theLosingTransactionLeavesNoOrphanPayslips() throws Exception {
        inParallel(List.of(
                () -> createRun(hrToken, MONTH_YEAR, MONTH).andReturn().getResponse().getStatus(),
                () -> createRun(adminToken, MONTH_YEAR, MONTH).andReturn().getResponse().getStatus()));

        // Three employees carry a salary (HR, staff, other); the bootstrap admin does not.
        Long payslips = jdbc.queryForObject("select count(*) from payslips", Long.class);
        Long orphans = jdbc.queryForObject("""
                select count(*) from payslips p
                 where not exists (select 1 from payroll_runs r where r.id = p.run_id)
                """, Long.class);
        assertThat(payslips).isEqualTo(3L);
        assertThat(orphans).isZero();
    }

    @Test
    void sixSimultaneousRunsStillLeaveExactlyOne() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> createRun(hrToken, MONTH_YEAR, MONTH).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = inParallel(tasks);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).allMatch(status -> status == 201 || status == 409);
        assertThat(jdbc.queryForObject("select count(*) from payroll_runs", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from payslips", Long.class)).isEqualTo(3L);
    }
}
