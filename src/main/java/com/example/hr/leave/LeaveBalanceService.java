package com.example.hr.leave;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.common.BadRequestException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.leave.LeaveDtos.LeaveBalanceResponse;
import com.example.hr.security.CurrentEmployee;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leave balances: lazy creation, the pro-rating rule for new joiners, and the locked
 * read used by every piece of balance arithmetic.
 *
 * <h2>Pro-rating formula</h2>
 * A balance row is created the first time it is needed (a request, an accrual run or a
 * balance query). The entitlement it starts with is:
 *
 * <pre>
 *   year &lt; hire year   -> 0
 *   year &gt; hire year   -> annualAllowanceDays                      (a full year of service)
 *   year = hire year   -> round(annualAllowanceDays × (13 − hireMonth) / 12)
 * </pre>
 *
 * {@code 13 − hireMonth} is the number of calendar months the employee is present in
 * their hire year, counting the hire month itself as a whole month (hired in January →
 * 12 months, in July → 6, in December → 1). Rounding is half-up, so 21 days with a July
 * hire date gives {@code round(10.5) = 11} days. Examples for ANNUAL (21 days):
 * Jan 21, Apr 16, Jul 11, Oct 5, Dec 2.
 *
 * <p>When {@code app.leave.annual.upfront} is set to {@code false} the ANNUAL entitlement
 * starts at 0 instead and is built up month by month by {@link LeaveAccrualService} — the
 * two policies are alternatives, never both.
 */
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository leaveTypes;
    private final EmployeeRepository employees;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;
    private final Clock clock;
    private final boolean annualUpfront;

    LeaveBalanceService(LeaveBalanceRepository balances, LeaveTypeRepository leaveTypes,
                        EmployeeRepository employees, EmployeeAccessPolicy policy,
                        CurrentEmployee currentEmployee, Clock clock,
                        @Value("${app.leave.annual.upfront:true}") boolean annualUpfront) {
        this.balances = balances;
        this.leaveTypes = leaveTypes;
        this.employees = employees;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
        this.clock = clock;
        this.annualUpfront = annualUpfront;
    }

    // ------------------------------------------------------------------ pure rules

    /** The pro-rating rule documented on this class. Pure and therefore unit-tested directly. */
    public static int initialEntitlement(int annualAllowanceDays, LocalDate hireDate, int year) {
        if (annualAllowanceDays <= 0 || year < hireDate.getYear()) {
            return 0;
        }
        if (year > hireDate.getYear()) {
            return annualAllowanceDays;
        }
        int monthsPresent = 13 - hireDate.getMonthValue();
        return (int) Math.round((double) annualAllowanceDays * monthsPresent / 12.0);
    }

    // ------------------------------------------------------------------ API

    @Transactional
    public List<LeaveBalanceResponse> myBalances(Integer year) {
        Employee me = currentEmployee.require();
        return balancesOf(me, resolveYear(year));
    }

    /** Self, any ancestor manager and HR/ADMIN; anybody else gets 404, so ids do not leak. */
    @Transactional
    public List<LeaveBalanceResponse> balancesOf(UUID employeeId, Integer year) {
        Employee actor = currentEmployee.require();
        Employee target = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("No employee with id " + employeeId));
        policy.requireCanViewProfile(actor, target);
        return balancesOf(target, resolveYear(year));
    }

    private List<LeaveBalanceResponse> balancesOf(Employee employee, int year) {
        ensureBalances(employee, year);
        Map<LeaveType, LeaveBalance> rows = byType(
                balances.findByEmployeeIdAndYearOrderByLeaveTypeAsc(employee.getId(), year));

        List<LeaveBalanceResponse> response = new ArrayList<>();
        for (LeaveTypeDefinition definition : leaveTypes.findAllByOrderByCodeAsc()) {
            LeaveBalance balance = rows.get(definition.getCode());
            if (balance == null) {
                continue;
            }
            response.add(new LeaveBalanceResponse(
                    definition.getCode(), year, definition.isPaid(), definition.isRequiresBalance(),
                    balance.getEntitledDays(), balance.getCarriedOverDays(), balance.getUsedDays(),
                    balance.getPendingDays(),
                    definition.isRequiresBalance() ? balance.remainingDays() : null));
        }
        return response;
    }

    // ------------------------------------------------------------------ internals used by the other services

    /**
     * Creates the missing balance rows for one employee and year. Each insert is an
     * {@code INSERT … ON CONFLICT DO NOTHING}, so concurrent first-time requests cannot
     * collide.
     */
    @Transactional
    public void ensureBalances(Employee employee, int year) {
        for (LeaveTypeDefinition definition : leaveTypes.findAllByOrderByCodeAsc()) {
            balances.insertIfAbsent(UUID.randomUUID(), employee.getId(), definition.getCode().name(), year,
                    startingEntitlement(definition, employee, year), 0);
        }
    }

    /**
     * Every balance row of this employee and year, locked {@code FOR UPDATE}. All callers
     * that change balances go through here first; see
     * {@link LeaveBalanceRepository#lockForUpdate(UUID, int)}.
     */
    @Transactional
    public Map<LeaveType, LeaveBalance> lockBalances(Employee employee, int year) {
        ensureBalances(employee, year);
        return byType(balances.lockForUpdate(employee.getId(), year));
    }

    @Transactional
    public LeaveBalance lockBalance(Employee employee, LeaveType type, int year) {
        LeaveBalance balance = lockBalances(employee, year).get(type);
        if (balance == null) {
            throw new IllegalStateException("No " + type + " balance for employee " + employee.getId()
                    + " in " + year);
        }
        return balance;
    }

    public int resolveYear(Integer year) {
        int resolved = year == null ? LocalDate.now(clock).getYear() : year;
        if (resolved < 2000 || resolved > 2100) {
            throw new BadRequestException("year must be between 2000 and 2100");
        }
        return resolved;
    }

    private int startingEntitlement(LeaveTypeDefinition definition, Employee employee, int year) {
        if (!definition.isRequiresBalance()) {
            return 0;
        }
        if (definition.getCode() == LeaveType.ANNUAL && !annualUpfront) {
            return 0;
        }
        return initialEntitlement(definition.getAnnualAllowanceDays(), employee.getHireDate(), year);
    }

    private static Map<LeaveType, LeaveBalance> byType(List<LeaveBalance> rows) {
        Map<LeaveType, LeaveBalance> map = new EnumMap<>(LeaveType.class);
        for (LeaveBalance row : rows) {
            map.put(row.getLeaveType(), row);
        }
        return map;
    }
}
