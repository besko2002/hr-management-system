package com.example.hr.leave;

import com.example.hr.common.BadRequestException;
import com.example.hr.common.ConflictException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.employee.Employee;
import com.example.hr.employee.dto.PageResponse;
import com.example.hr.leave.LeaveDtos.CreateLeaveRequestBody;
import com.example.hr.leave.LeaveDtos.LeaveRequestResponse;
import com.example.hr.security.CurrentEmployee;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The leave workflow: filing a request, the authority checks around a decision, the state
 * machine and — above all — the balance bookkeeping, which must always add up.
 *
 * <h2>Balance accounting</h2>
 * <pre>
 *   file     : pending += days                     (the days are reserved, not yet spent)
 *   approve  : pending -= days, used += days
 *   reject   : pending -= days                     (full refund)
 *   cancel   : PENDING  -> pending -= days         (full refund)
 *              APPROVED -> used    -= days         (full refund, only before it starts)
 * </pre>
 * After any sequence of transitions the row is back to the exact numbers it had before.
 *
 * <h2>Why two simultaneous requests cannot overspend</h2>
 * {@link #create} takes a {@code SELECT … FOR UPDATE} on <em>all</em> balance rows of the
 * employee for that year <em>before</em> it reads the remaining days, checks the overlap
 * or reserves anything, and holds the lock until commit. Concurrent requests for the same
 * employee therefore execute strictly one after another, each seeing the other's
 * reservation, so with a balance for K days exactly K requests succeed and the rest get
 * 409. The rows also carry an optimistic {@code version} as a backstop.
 */
@Service
public class LeaveRequestService {

    /** The states that make a day "taken" and therefore block an overlapping request. */
    private static final Set<LeaveStatus> BLOCKING = blocking();

    private static Set<LeaveStatus> blocking() {
        EnumSet<LeaveStatus> statuses = EnumSet.noneOf(LeaveStatus.class);
        for (LeaveStatus status : LeaveStatus.values()) {
            if (status.blocksOverlap()) {
                statuses.add(status);
            }
        }
        return Set.copyOf(statuses);
    }

    private final LeaveRequestRepository requests;
    private final LeaveBalanceService balanceService;
    private final LeaveTypeRepository leaveTypes;
    private final HolidayRepository holidays;
    private final WorkingDayCalculator calculator;
    private final LeaveDecisionPolicy decisionPolicy;
    private final CurrentEmployee currentEmployee;
    private final Clock clock;
    private final int maxPageSize;

    LeaveRequestService(LeaveRequestRepository requests, LeaveBalanceService balanceService,
                        LeaveTypeRepository leaveTypes, HolidayRepository holidays,
                        WorkingDayCalculator calculator, LeaveDecisionPolicy decisionPolicy,
                        CurrentEmployee currentEmployee, Clock clock,
                        @Value("${app.leave.max-page-size:100}") int maxPageSize) {
        this.requests = requests;
        this.balanceService = balanceService;
        this.leaveTypes = leaveTypes;
        this.holidays = holidays;
        this.calculator = calculator;
        this.decisionPolicy = decisionPolicy;
        this.currentEmployee = currentEmployee;
        this.clock = clock;
        this.maxPageSize = maxPageSize;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public LeaveRequestResponse create(CreateLeaveRequestBody body) {
        Employee me = currentEmployee.require();
        if (!me.isActive()) {
            throw new ConflictException("A terminated employee cannot request leave");
        }

        LocalDate start = body.startDate();
        LocalDate end = body.endDate();
        LocalDate today = LocalDate.now(clock);

        if (end.isBefore(start)) {
            throw new BadRequestException("endDate must not be before startDate");
        }
        if (start.getYear() < today.getYear()) {
            throw new BadRequestException("Leave cannot be requested for a past year");
        }
        if (start.isBefore(me.getHireDate())) {
            throw new BadRequestException("Leave cannot start before the hire date (" + me.getHireDate() + ")");
        }
        if (start.isAfter(today.plusYears(1))) {
            throw new BadRequestException("Leave cannot start more than one year ahead");
        }
        if (start.getYear() != end.getYear()) {
            throw new BadRequestException(
                    "A leave request must not span two calendar years; file one request per year");
        }

        int days = calculator.workingDays(start, end, holidays.findDatesBetween(start, end));
        if (days == 0) {
            throw new BadRequestException("The requested range contains no working days: "
                    + start + " to " + end + " is only weekend days and holidays");
        }

        int year = start.getYear();
        LeaveTypeDefinition definition = requireType(body.type());

        // One serialisation point for everything below: balances first, then the overlap
        // check, then the reservation — all under the same row locks, held until commit.
        LeaveBalance balance = balanceService.lockBalance(me, body.type(), year);

        if (requests.overlaps(me.getId(), start, end, BLOCKING)) {
            throw new ConflictException("This range overlaps one of your pending or approved leave requests");
        }
        if (definition.isRequiresBalance() && balance.remainingDays() < days) {
            throw new ConflictException("Not enough " + body.type() + " balance for " + year + ": "
                    + days + " working day(s) requested but only " + balance.remainingDays() + " remaining");
        }

        balance.reserve(days);
        LeaveRequest request = requests.save(
                new LeaveRequest(me, body.type(), start, end, days, trimToNull(body.reason())));
        return LeaveMapper.toResponse(request);
    }

    // ------------------------------------------------------------------ decisions

    @Transactional
    public LeaveRequestResponse approve(UUID id, String note) {
        Employee actor = currentEmployee.require();
        LeaveRequest request = requireForUpdate(id);
        Employee requester = request.getEmployee();
        decisionPolicy.requireCanDecide(actor, requester, id);
        requireTransitionAllowed(request, LeaveStatus.APPROVED);

        balance(request).consumeReserved(request.getWorkingDays());
        request.approve(actor, trimToNull(note));
        return LeaveMapper.toResponse(requests.save(request));
    }

    @Transactional
    public LeaveRequestResponse reject(UUID id, String note) {
        Employee actor = currentEmployee.require();
        LeaveRequest request = requireForUpdate(id);
        Employee requester = request.getEmployee();
        decisionPolicy.requireCanDecide(actor, requester, id);
        requireTransitionAllowed(request, LeaveStatus.REJECTED);

        String trimmed = trimToNull(note);
        if (trimmed == null) {
            throw new BadRequestException("decisionNote is required when rejecting a leave request");
        }

        balance(request).releaseReserved(request.getWorkingDays());
        request.reject(actor, trimmed);
        return LeaveMapper.toResponse(requests.save(request));
    }

    /**
     * Cancellation by the requester (or HR/ADMIN). Always allowed while PENDING; for an
     * APPROVED leave only while it has not started yet, because days already taken cannot
     * be given back.
     */
    @Transactional
    public LeaveRequestResponse cancel(UUID id, String note) {
        Employee actor = currentEmployee.require();
        LeaveRequest request = requireForUpdate(id);
        decisionPolicy.requireCanCancel(actor, request.getEmployee(), id);
        requireTransitionAllowed(request, LeaveStatus.CANCELLED);

        LeaveStatus previous = request.getStatus();
        if (previous == LeaveStatus.APPROVED && !request.getStartDate().isAfter(LocalDate.now(clock))) {
            throw new ConflictException("An approved leave can only be cancelled before it starts (it starts on "
                    + request.getStartDate() + ")");
        }

        LeaveBalance balance = balance(request);
        if (previous == LeaveStatus.PENDING) {
            balance.releaseReserved(request.getWorkingDays());
        } else {
            balance.releaseUsed(request.getWorkingDays());
        }
        request.cancel(actor, trimToNull(note));
        return LeaveMapper.toResponse(requests.save(request));
    }

    // ------------------------------------------------------------------ reads

    /** The requester, their ancestors and HR/ADMIN; anybody else gets 404. */
    @Transactional(readOnly = true)
    public LeaveRequestResponse get(UUID id) {
        Employee actor = currentEmployee.require();
        LeaveRequest request = require(id);
        decisionPolicy.requireCanView(actor, request.getEmployee(), id);
        return LeaveMapper.toResponse(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> myRequests(LeaveStatus status, Integer year, int page, int size) {
        Employee me = currentEmployee.require();
        Page<LeaveRequest> found = requests.findAll(
                LeaveRequestSpecifications.of(me.getId(), status, year), pageable(page, size));
        return PageResponse.of(found, LeaveMapper::toResponse);
    }

    /** The caller's direct reports' PENDING requests; HR/ADMIN see every pending request. */
    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> pendingForDecision(int page, int size) {
        Employee me = currentEmployee.require();
        Pageable pageable = pageable(page, size);
        Page<LeaveRequest> found = me.getRole().isHrOrAdmin()
                ? requests.findByStatus(LeaveStatus.PENDING, pageable)
                : requests.findByManager(me.getId(), LeaveStatus.PENDING, pageable);
        return PageResponse.of(found, LeaveMapper::toResponse);
    }

    // ------------------------------------------------------------------ helpers

    private LeaveBalance balance(LeaveRequest request) {
        return balanceService.lockBalance(request.getEmployee(), request.getLeaveType(), request.getLeaveYear());
    }

    private void requireTransitionAllowed(LeaveRequest request, LeaveStatus target) {
        if (!request.getStatus().canTransitionTo(target)) {
            throw new ConflictException("This leave request is " + request.getStatus()
                    + " and can no longer become " + target);
        }
    }

    private LeaveRequest require(UUID id) {
        return requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No leave request with id " + id));
    }

    /** Every state change starts here: the row is locked before its status is examined. */
    private LeaveRequest requireForUpdate(UUID id) {
        return requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("No leave request with id " + id));
    }

    private LeaveTypeDefinition requireType(LeaveType type) {
        return leaveTypes.findById(type)
                .orElseThrow(() -> new BadRequestException("Unknown leave type " + type));
    }

    private Pageable pageable(int page, int size) {
        int capped = size < 1 ? 20 : Math.min(size, maxPageSize);
        return PageRequest.of(Math.max(page, 0), capped,
                Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("createdAt")));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Package-private view used by the termination hook and the tests. */
    static Set<LeaveStatus> blockingStatuses() {
        return BLOCKING;
    }
}
