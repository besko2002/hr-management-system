package com.example.hr.payroll;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.attendance.AttendanceDeriver;
import com.example.hr.attendance.DailyAttendance;
import com.example.hr.attendance.WorkCalendar;
import com.example.hr.common.BadRequestException;
import com.example.hr.common.ConflictException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeRepository;
import com.example.hr.employee.dto.PageResponse;
import com.example.hr.payroll.PayrollDtos.CreateRunBody;
import com.example.hr.payroll.PayrollDtos.PayrollRunDetailResponse;
import com.example.hr.payroll.PayrollDtos.PayrollRunResponse;
import com.example.hr.payroll.PayrollDtos.PayrollTotals;
import com.example.hr.payroll.PayrollDtos.PayslipResponse;
import com.example.hr.security.CurrentEmployee;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The payroll run lifecycle and the payslip access rules.
 *
 * <h2>Run model</h2>
 * {@code POST /api/payroll/runs} creates a DRAFT run plus one immutable payslip per
 * eligible employee in a <b>single transaction</b>. Idempotency is enforced by the
 * {@code (run_year, run_month)} unique constraint, not by a pre-check: the run row is
 * inserted and flushed first, so two concurrent calls serialise on it and the loser's
 * whole transaction — run and payslips — is rolled back with a 409. Months in the future
 * are refused; the current month is allowed (and then only the attendance recorded so far
 * counts).
 *
 * <p>DRAFT runs can be recalculated (payslips are replaced) or deleted. FINALIZED runs are
 * frozen: recalculate and delete answer 409, and because every figure is stored, later
 * attendance corrections or salary changes cannot move a single number.
 *
 * <h2>Who sees a payslip</h2>
 * <ul>
 *   <li>HR and ADMIN: always, DRAFT included.</li>
 *   <li>The employee themself: only once the run is FINALIZED. A DRAFT payslip answers
 *       404 to them.</li>
 *   <li>A manager: <b>never</b> — not even for a direct report. 404, the same answer an
 *       unrelated employee gets, so payslip ids cannot be probed.</li>
 * </ul>
 */
@Service
public class PayrollService {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final EmployeeRepository employees;
    private final AttendanceDeriver attendance;
    private final WorkCalendar calendar;
    private final PayrollCalculator calculator;
    private final PayrollRates rates;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;
    private final ObjectMapper json;
    private final Clock clock;

    PayrollService(PayrollRunRepository runs, PayslipRepository payslips, EmployeeRepository employees,
                   AttendanceDeriver attendance, WorkCalendar calendar, PayrollCalculator calculator,
                   PayrollRates rates, EmployeeAccessPolicy policy, CurrentEmployee currentEmployee,
                   ObjectMapper json, Clock clock) {
        this.runs = runs;
        this.payslips = payslips;
        this.employees = employees;
        this.attendance = attendance;
        this.calendar = calendar;
        this.calculator = calculator;
        this.rates = rates;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
        this.json = json;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ run lifecycle

    @Transactional
    public PayrollRunDetailResponse createRun(CreateRunBody body) {
        Employee actor = currentEmployee.require();
        policy.requireHrOrAdmin(actor, "run payroll");

        YearMonth period = YearMonth.of(body.year(), body.month());
        if (period.isAfter(YearMonth.from(attendance.today()))) {
            throw new BadRequestException("Payroll cannot be run for a future month (" + period + ")");
        }
        if (runs.existsByYearAndMonth(period.getYear(), period.getMonthValue())) {
            throw new ConflictException("A payroll run for " + period + " already exists");
        }

        PayrollRun run = new PayrollRun(period, actor, now());
        try {
            // The real guard: the unique (year, month) index. A concurrent caller blocks
            // here and then fails, rolling back its run *and* its payslips.
            runs.saveAndFlush(run);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A payroll run for " + period + " already exists");
        }

        List<Payslip> created = buildPayslips(run);
        return detail(run, created);
    }

    @Transactional
    public PayrollRunDetailResponse recalculate(UUID runId) {
        policy.requireHrOrAdmin(currentEmployee.require(), "recalculate a payroll run");
        PayrollRun run = requireRun(runId);
        run.requireDraft("recalculated");

        payslips.deleteByRunId(run.getId());
        payslips.flush();
        return detail(run, buildPayslips(run));
    }

    @Transactional
    public PayrollRunResponse finalizeRun(UUID runId) {
        Employee actor = currentEmployee.require();
        policy.requireHrOrAdmin(actor, "finalize a payroll run");
        PayrollRun run = requireRun(runId);
        run.finalizeRun(actor, now());
        runs.save(run);
        return toRun(run);
    }

    @Transactional
    public void deleteRun(UUID runId) {
        policy.requireHrOrAdmin(currentEmployee.require(), "delete a payroll run");
        PayrollRun run = requireRun(runId);
        run.requireDraft("deleted");
        payslips.deleteByRunId(run.getId());
        payslips.flush();
        runs.delete(run);
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public PageResponse<PayrollRunResponse> listRuns(int page, int size) {
        policy.requireHrOrAdmin(currentEmployee.require(), "list payroll runs");
        int resolved = size < 1 ? 20 : Math.min(size, 100);
        return PageResponse.of(
                runs.findAllByOrderByYearDescMonthDesc(PageRequest.of(Math.max(page, 0), resolved)),
                this::toRun);
    }

    @Transactional(readOnly = true)
    public PayrollRunDetailResponse getRun(UUID runId) {
        policy.requireHrOrAdmin(currentEmployee.require(), "view a payroll run");
        PayrollRun run = requireRun(runId);
        return detail(run, payslips.findByRun(run.getId()));
    }

    @Transactional(readOnly = true)
    public List<PayslipResponse> myPayslips() {
        Employee me = currentEmployee.require();
        return payslips.findFinalizedOf(me.getId()).stream().map(this::toPayslip).toList();
    }

    @Transactional(readOnly = true)
    public PayslipResponse getPayslip(UUID payslipId) {
        return toPayslip(requireVisiblePayslip(payslipId));
    }

    /**
     * Resolves a payslip and applies the privacy matrix. Everything that is not allowed is
     * a 404 — never a 403 — so the existence of a payslip is never confirmed to somebody
     * who may not read it.
     */
    @Transactional(readOnly = true)
    public Payslip requireVisiblePayslip(UUID payslipId) {
        Employee actor = currentEmployee.require();
        Payslip payslip = payslips.findById(payslipId)
                .orElseThrow(() -> notFound(payslipId));
        if (policy.isHrOrAdmin(actor)) {
            return payslip;
        }
        boolean own = payslip.employeeId().equals(actor.getId());
        if (own && payslip.getRun().getStatus().isFinalized()) {
            return payslip;
        }
        throw notFound(payslipId);
    }

    @Transactional(readOnly = true)
    public PayrollRun requireRunForExport(UUID runId) {
        policy.requireHrOrAdmin(currentEmployee.require(), "export a payroll run");
        return requireRun(runId);
    }

    @Transactional(readOnly = true)
    public List<Payslip> payslipsOf(UUID runId) {
        return payslips.findByRun(runId);
    }

    // ------------------------------------------------------------------ the run itself

    private List<Payslip> buildPayslips(PayrollRun run) {
        YearMonth period = run.period();
        LocalDate monthStart = period.atDay(1);
        LocalDate monthEnd = period.atEndOfMonth();

        List<Employee> eligible = employees.findPayrollEligible(monthStart, monthEnd);
        Set<LocalDate> holidays = calendar.holidaysBetween(monthStart, monthEnd);
        int workingDaysInMonth = calendar.workingDaysBetween(monthStart, monthEnd, holidays);
        Map<UUID, List<DailyAttendance>> derived = attendance.deriveAll(eligible, monthStart, monthEnd);
        Instant now = now();

        List<Payslip> created = new ArrayList<>();
        for (Employee employee : eligible) {
            LocalDate windowStart = max(monthStart, employee.getHireDate());
            LocalDate windowEnd = employee.getTerminatedAt() == null
                    ? monthEnd : min(monthEnd, employee.getTerminatedAt());
            int payableDays = windowEnd.isBefore(windowStart)
                    ? 0 : calendar.workingDaysBetween(windowStart, windowEnd, holidays);

            int unpaidLeaveDays = 0;
            int absentDays = 0;
            int lateMinutes = 0;
            int overtimeMinutes = 0;
            int holidayMinutes = 0;
            for (DailyAttendance day : derived.getOrDefault(employee.getId(), List.of())) {
                if (day.isUnpaidLeaveDay()) {
                    unpaidLeaveDays++;
                }
                if (day.isAbsentDay()) {
                    absentDays++;
                }
                lateMinutes += day.lateMinutes();
                overtimeMinutes += day.weekdayOvertimeMinutes();
                holidayMinutes += day.restDayMinutes();
            }

            PayrollInput input = new PayrollInput(employee.getSalary(), workingDaysInMonth,
                    Math.min(payableDays, workingDaysInMonth), unpaidLeaveDays, absentDays, lateMinutes,
                    overtimeMinutes, holidayMinutes, rates);
            PayrollResult result = calculator.calculate(input);
            created.add(payslips.save(new Payslip(run, employee, result,
                    writeSnapshot(period, monthStart, monthEnd, result), now)));
        }
        payslips.flush();
        return created;
    }

    private String writeSnapshot(YearMonth period, LocalDate start, LocalDate end, PayrollResult result) {
        PayslipSnapshot snapshot = new PayslipSnapshot(period.getYear(), period.getMonthValue(), start, end,
                result.rates(), result.taxBreakdown(), PayslipSnapshot.NOTES);
        try {
            return json.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise the payslip snapshot", ex);
        }
    }

    private PayslipSnapshot readSnapshot(Payslip payslip) {
        try {
            return json.readValue(payslip.getSnapshot(), PayslipSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read the payslip snapshot of " + payslip.getId(), ex);
        }
    }

    // ------------------------------------------------------------------ mapping

    PayrollRunResponse toRun(PayrollRun run) {
        return new PayrollRunResponse(run.getId(), run.getYear(), run.getMonth(), run.getStatus(),
                (int) payslips.countByRunId(run.getId()),
                run.getCreatedBy() == null ? null : run.getCreatedBy().getId(), run.getCreatedAt(),
                run.getFinalizedBy() == null ? null : run.getFinalizedBy().getId(), run.getFinalizedAt());
    }

    PayrollRunDetailResponse detail(PayrollRun run, List<Payslip> slips) {
        return new PayrollRunDetailResponse(toRun(run), totals(slips),
                slips.stream().map(this::toPayslip).toList());
    }

    static PayrollTotals totals(List<Payslip> slips) {
        var base = Money.ZERO;
        var prorated = Money.ZERO;
        var overtime = Money.ZERO;
        var holiday = Money.ZERO;
        var unpaid = Money.ZERO;
        var absence = Money.ZERO;
        var gross = Money.ZERO;
        var insurance = Money.ZERO;
        var tax = Money.ZERO;
        var net = Money.ZERO;
        for (Payslip slip : slips) {
            base = base.add(slip.getBaseSalary());
            prorated = prorated.add(slip.getProratedBase());
            overtime = overtime.add(slip.getOvertimePay());
            holiday = holiday.add(slip.getHolidayPay());
            unpaid = unpaid.add(slip.getUnpaidLeaveDeduction());
            absence = absence.add(slip.getAbsenceDeduction());
            gross = gross.add(slip.getGrossEarnings());
            insurance = insurance.add(slip.getInsurance());
            tax = tax.add(slip.getTax());
            net = net.add(slip.getNetPay());
        }
        return new PayrollTotals(slips.size(), Money.scaled(base), Money.scaled(prorated),
                Money.scaled(overtime), Money.scaled(holiday), Money.scaled(unpaid), Money.scaled(absence),
                Money.scaled(gross), Money.scaled(insurance), Money.scaled(tax), Money.scaled(net));
    }

    PayslipResponse toPayslip(Payslip p) {
        PayslipSnapshot snapshot = readSnapshot(p);
        return new PayslipResponse(p.getId(), p.getRun().getId(), p.getRun().getYear(),
                p.getRun().getMonth(), p.getRun().getStatus(), p.employeeId(), p.getEmployeeNumber(),
                p.getEmployeeName(), p.getJobTitle(), p.getDepartmentName(), p.getHireDate(),
                p.getTerminatedAt(), p.getBaseSalary(), p.getWorkingDaysInMonth(),
                p.getPayableWorkingDays(), p.getUnpaidLeaveDays(), p.getAbsentDays(), p.getLateMinutes(),
                p.getOvertimeMinutes(), p.getHolidayMinutes(), p.getDailyRate(), p.getHourlyRate(),
                p.getOvertimeHours(), p.getHolidayHours(), p.getProratedBase(), p.getOvertimePay(),
                p.getHolidayPay(), p.getUnpaidLeaveDeduction(), p.getAbsenceDeduction(),
                p.getGrossEarnings(), p.getInsurableWage(), p.getInsurance(), p.getPersonalExemption(),
                p.getTaxableIncome(), p.getTax(), p.getNetPay(), p.isNetFloored(),
                p.isNetFloored()
                        ? "Deductions exceeded earnings; the net pay was floored to 0.00"
                        : null,
                snapshot.taxBreakdown(), snapshot.rates(), p.getCreatedAt());
    }

    // ------------------------------------------------------------------ helpers

    private Instant now() {
        return clock.instant();
    }

    private PayrollRun requireRun(UUID runId) {
        return runs.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("No payroll run with id " + runId));
    }

    private static ResourceNotFoundException notFound(UUID payslipId) {
        return new ResourceNotFoundException("No payslip with id " + payslipId);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
