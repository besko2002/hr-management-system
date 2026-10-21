package com.example.hr.leave;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.common.BadRequestException;
import com.example.hr.common.ConflictException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.employee.EmployeeStatus;
import com.example.hr.leave.LeaveDtos.AccrualRunResponse;
import com.example.hr.security.CurrentEmployee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

/**
 * Monthly ANNUAL accrual: every ACTIVE employee gains one twelfth of the yearly allowance.
 *
 * <h2>Whole days without drift</h2>
 * Days are integers, and 21/12 is not. Instead of rounding each month on its own (which
 * would drift to 24 or to 12 days a year), the credit for month <em>m</em> is the
 * difference between the cumulative targets:
 * {@code round(allowance × m / 12) − round(allowance × (m−1) / 12)}. For 21 days that is
 * 2,2,1,2,2,2,1,2,2,2,1,2 — exactly 21 days over a full year.
 *
 * <h2>Idempotency</h2>
 * Crediting is guarded by the {@code accrual_log} unique key
 * (employee, type, year, month): the insert is an {@code ON CONFLICT DO NOTHING} and the
 * entitlement is raised only when that insert actually created the row. A re-run, a
 * double fire of the scheduler or two instances racing therefore add nothing twice; the
 * whole run is one transaction.
 */
@Slf4j
@Service
public class LeaveAccrualService {

    private final EmployeeRepository employees;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveBalanceService balanceService;
    private final AccrualLogRepository accrualLog;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;
    private final Clock clock;
    private final boolean annualUpfront;

    LeaveAccrualService(EmployeeRepository employees, LeaveTypeRepository leaveTypes,
                        LeaveBalanceService balanceService, AccrualLogRepository accrualLog,
                        EmployeeAccessPolicy policy, CurrentEmployee currentEmployee, Clock clock,
                        @Value("${app.leave.annual.upfront:true}") boolean annualUpfront) {
        this.employees = employees;
        this.leaveTypes = leaveTypes;
        this.balanceService = balanceService;
        this.accrualLog = accrualLog;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
        this.clock = clock;
        this.annualUpfront = annualUpfront;
    }

    /** The message used both by the run guard and by the startup guard of the scheduler. */
    static final String UPFRONT_CONFLICT_MESSAGE =
            "Monthly accrual is disabled while app.leave.annual.upfront=true; ANNUAL is granted upfront";

    /** The whole-day credit for one month; see the class comment. */
    public static int monthlyCredit(int annualAllowanceDays, int month) {
        if (annualAllowanceDays <= 0) {
            return 0;
        }
        return cumulative(annualAllowanceDays, month) - cumulative(annualAllowanceDays, month - 1);
    }

    private static int cumulative(int annualAllowanceDays, int month) {
        return (int) Math.round((double) annualAllowanceDays * month / 12.0);
    }

    /** HR/ADMIN only: {@code POST /api/leave/accrual/run}. */
    @Transactional
    public AccrualRunResponse runOnDemand(Integer year, Integer month) {
        policy.requireHrOrAdmin(currentEmployee.require(), "run the leave accrual");
        LocalDate today = LocalDate.now(clock);
        return runFor(year == null ? today.getYear() : year, month == null ? today.getMonthValue() : month);
    }

    /** Called by the scheduler and directly by tests; carries no security context of its own. */
    @Transactional
    public AccrualRunResponse runForCurrentMonth() {
        LocalDate today = LocalDate.now(clock);
        return runFor(today.getYear(), today.getMonthValue());
    }

    /**
     * Credits the ANNUAL entitlement of every ACTIVE employee hired on or before the last
     * day of (year, month). Safe to call any number of times.
     *
     * <p>The single entry point of every caller (the HR endpoint, the scheduler and the
     * tests), and therefore the one place that enforces that upfront granting and monthly
     * accrual are <em>alternatives</em>: with {@code app.leave.annual.upfront=true} the
     * whole (pro-rated) year is already in {@code entitled}, so accruing on top would push
     * everybody past the yearly allowance. That is refused with 409 before any row is read
     * or written.
     */
    @Transactional
    public AccrualRunResponse runFor(int year, int month) {
        if (annualUpfront) {
            throw new ConflictException(UPFRONT_CONFLICT_MESSAGE);
        }
        if (month < 1 || month > 12) {
            throw new BadRequestException("month must be between 1 and 12");
        }
        if (year < 2000 || year > 2100) {
            throw new BadRequestException("year must be between 2000 and 2100");
        }

        LeaveTypeDefinition annual = leaveTypes.findById(LeaveType.ANNUAL)
                .orElseThrow(() -> new IllegalStateException("The ANNUAL leave type is not seeded"));
        int days = monthlyCredit(annual.getAnnualAllowanceDays(), month);
        LocalDate endOfMonth = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());

        List<Employee> candidates =
                employees.findByStatusAndHireDateLessThanEqual(EmployeeStatus.ACTIVE, endOfMonth);

        int credited = 0;
        int alreadyCredited = 0;
        for (Employee employee : candidates) {
            boolean claimed = accrualLog.claimMonth(UUID.randomUUID(), employee.getId(),
                    LeaveType.ANNUAL.name(), year, month, days) == 1;
            if (!claimed) {
                alreadyCredited++;
                continue;
            }
            if (days > 0) {
                balanceService.lockBalance(employee, LeaveType.ANNUAL, year).credit(days);
            }
            credited++;
        }

        log.info("Leave accrual {}-{}: {} of {} employees credited with {} ANNUAL day(s), {} already had it",
                year, month, credited, candidates.size(), days, alreadyCredited);
        return new AccrualRunResponse(year, month, LeaveType.ANNUAL, days, candidates.size(), credited,
                alreadyCredited);
    }
}
