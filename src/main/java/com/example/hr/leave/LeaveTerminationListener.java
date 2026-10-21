package com.example.hr.leave;

import com.example.hr.employee.Employee;
import com.example.hr.employee.EmployeeTerminationListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * When an employee is terminated, every PENDING request of theirs becomes CANCELLED and
 * the days it had reserved are refunded to the balance. Runs inside the termination
 * transaction, so a failure here rolls the termination back as well.
 *
 * <p>APPROVED leave is deliberately left alone: it is a decision that was already taken
 * and, for a leaver, the record of days actually consumed.
 */
@Slf4j
@Component
class LeaveTerminationListener implements EmployeeTerminationListener {

    private final LeaveRequestRepository requests;
    private final LeaveBalanceService balanceService;

    LeaveTerminationListener(LeaveRequestRepository requests, LeaveBalanceService balanceService) {
        this.requests = requests;
        this.balanceService = balanceService;
    }

    @Override
    public void onTerminated(Employee employee) {
        List<LeaveRequest> candidates = requests.findByEmployeeIdAndStatusOrderByStartDateAsc(
                employee.getId(), LeaveStatus.PENDING);
        int cancelled = 0;
        for (LeaveRequest candidate : candidates) {
            // Re-read under a row lock: a manager may have decided the request between the
            // listing above and this line, in which case there is nothing left to refund.
            LeaveRequest request = requests.findByIdForUpdate(candidate.getId()).orElse(null);
            if (request == null || request.getStatus() != LeaveStatus.PENDING) {
                continue;
            }
            balanceService.lockBalance(employee, request.getLeaveType(), request.getLeaveYear())
                    .releaseReserved(request.getWorkingDays());
            request.cancel(null, "Cancelled automatically: the employee was terminated");
            requests.save(request);
            cancelled++;
        }
        if (cancelled > 0) {
            log.info("Terminated employee {}: cancelled {} pending leave request(s) and refunded the reserved days",
                    employee.getId(), cancelled);
        }
    }
}
