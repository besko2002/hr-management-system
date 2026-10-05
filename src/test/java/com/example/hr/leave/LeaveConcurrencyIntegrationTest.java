package com.example.hr.leave;

import com.example.hr.employee.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real race: many requests filed at the same instant against a balance that can only
 * cover some of them. The check-and-reserve runs under a {@code SELECT … FOR UPDATE} on the
 * employee's balance rows, so the requests serialise and the balance can never be
 * overspent — exactly as many succeed as there are days to spend.
 */
class LeaveConcurrencyIntegrationTest extends AbstractLeaveIntegrationTest {

    private static final int THREADS = 8;

    private String staffToken;
    private String managerToken;

    @BeforeEach
    void setUp() throws Exception {
        String admin = adminToken();
        NewEmployee manager = createEmployee(admin, "Mary Manager", Role.EMPLOYEE, null);
        NewEmployee staff = createEmployee(admin, "Stan Staff", Role.EMPLOYEE, manager.id());
        managerToken = login(manager.email(), manager.password());
        staffToken = login(staff.email(), staff.password());
    }

    /** Runs {@code tasks} as simultaneously as the JVM allows and returns their results. */
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
    void withABalanceForFiveDaysExactlyFiveOfEightSimultaneousRequestsSucceed() throws Exception {
        // SICK is 10 days a year; park 5 of them in a pending request, leaving exactly 5.
        LocalDate parked = planningStart();
        fileDaysOk(staffToken, LeaveType.SICK, parked, 5);
        assertThat(remaining(staffToken, LeaveType.SICK, planningYear())).isEqualTo(5);

        // Eight distinct single-day slots, so nothing can collide on the overlap rule.
        List<LocalDate> slots = new ArrayList<>();
        LocalDate slot = nextWorkingDayAfter(endOf(parked, 5));
        for (int i = 0; i < THREADS; i++) {
            slots.add(slot);
            slot = nextWorkingDayAfter(slot);
        }

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (LocalDate day : slots) {
            tasks.add(() -> fileRequest(staffToken, LeaveType.SICK, day, day)
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = inParallel(tasks);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(5);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(THREADS - 5);
        assertThat(statuses).allMatch(status -> status == 201 || status == 409);

        // The balance is exactly spent, never overspent.
        assertThat(remaining(staffToken, LeaveType.SICK, planningYear())).isZero();
        assertThat(balance(staffToken, LeaveType.SICK, planningYear()).path("pendingDays").asInt())
                .isEqualTo(10);
    }

    @Test
    void simultaneousRequestsForTheSameRangeCannotBothBeFiled() throws Exception {
        LocalDate day = planningStart();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> fileRequest(staffToken, LeaveType.ANNUAL, day, day)
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = inParallel(tasks);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(3);
        assertThat(balance(staffToken, LeaveType.ANNUAL, planningYear()).path("pendingDays").asInt())
                .isEqualTo(1);
    }

    @Test
    void simultaneousApprovalsOfTheSameRequestOnlyBookTheDaysOnce() throws Exception {
        LocalDate start = planningStart();
        java.util.UUID request = fileDaysOk(staffToken, LeaveType.ANNUAL, start, 3);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> approve(managerToken, request).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = inParallel(tasks);

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        assertThat(statuses).filteredOn(status -> status != 200).hasSize(3);

        assertThat(balance(staffToken, LeaveType.ANNUAL, planningYear()).path("usedDays").asInt())
                .isEqualTo(3);
        assertThat(balance(staffToken, LeaveType.ANNUAL, planningYear()).path("pendingDays").asInt())
                .isZero();
    }

    @Test
    void parallelFirstTimeBalanceCreationDoesNotDuplicateRows() throws Exception {
        // Six callers touch a year whose balance rows do not exist yet; the upsert must
        // leave exactly one row per leave type.
        int year = planningYear() + 1;
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> getAs(staffToken, "/api/leave/balances/me?year=" + year)
                    .andReturn().getResponse().getStatus());
        }
        assertThat(inParallel(tasks)).allMatch(status -> status == 200);

        Long rows = jdbc.queryForObject("select count(*) from leave_balances where balance_year = ?",
                Long.class, year);
        assertThat(rows).isEqualTo(3L);
        getAs(staffToken, "/api/leave/balances/me?year=" + year).andExpect(status().isOk());
    }
}
