package com.example.hr.leave;

import com.example.hr.employee.dto.PageResponse;
import com.example.hr.leave.LeaveDtos.AccrualRunResponse;
import com.example.hr.leave.LeaveDtos.CreateLeaveRequestBody;
import com.example.hr.leave.LeaveDtos.DecisionBody;
import com.example.hr.leave.LeaveDtos.LeaveBalanceResponse;
import com.example.hr.leave.LeaveDtos.LeaveCalendarEntry;
import com.example.hr.leave.LeaveDtos.LeaveRequestResponse;
import com.example.hr.leave.LeaveDtos.LeaveTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave")
@Tag(name = "Leave")
class LeaveController {

    private final LeaveRequestService requests;
    private final LeaveBalanceService balances;
    private final LeaveCalendarService calendar;
    private final LeaveAccrualService accrual;
    private final LeaveTypeService leaveTypes;

    LeaveController(LeaveRequestService requests, LeaveBalanceService balances, LeaveCalendarService calendar,
                    LeaveAccrualService accrual, LeaveTypeService leaveTypes) {
        this.requests = requests;
        this.balances = balances;
        this.calendar = calendar;
        this.accrual = accrual;
        this.leaveTypes = leaveTypes;
    }

    // ------------------------------------------------------------------ types and balances

    @GetMapping("/types")
    @Operation(summary = "The configured leave types (HR/ADMIN)")
    List<LeaveTypeResponse> types() {
        return leaveTypes.list();
    }

    @GetMapping("/balances/me")
    @Operation(summary = "The caller's own balances; defaults to the current year")
    List<LeaveBalanceResponse> myBalances(@RequestParam(required = false) Integer year) {
        return balances.myBalances(year);
    }

    @GetMapping("/balances/{employeeId}")
    @Operation(summary = "One employee's balances: self, any ancestor manager, HR/ADMIN; 404 for anybody else")
    List<LeaveBalanceResponse> balancesOf(@PathVariable UUID employeeId,
                                          @RequestParam(required = false) Integer year) {
        return balances.balancesOf(employeeId, year);
    }

    // ------------------------------------------------------------------ requests

    @PostMapping("/requests")
    @Operation(summary = "File a leave request for yourself")
    ResponseEntity<LeaveRequestResponse> create(@Valid @RequestBody CreateLeaveRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requests.create(body));
    }

    @GetMapping("/requests/me")
    @Operation(summary = "The caller's own requests, paged, filterable by status and year")
    PageResponse<LeaveRequestResponse> myRequests(@RequestParam(required = false) LeaveStatus status,
                                                   @RequestParam(required = false) Integer year,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return requests.myRequests(status, year, page, size);
    }

    @GetMapping("/requests/pending")
    @Operation(summary = "Pending requests awaiting the caller: their direct reports'; HR/ADMIN see all")
    PageResponse<LeaveRequestResponse> pending(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return requests.pendingForDecision(page, size);
    }

    @GetMapping("/requests/{id}")
    @Operation(summary = "One request: the requester, their ancestors, HR/ADMIN; 404 for anybody else")
    LeaveRequestResponse get(@PathVariable UUID id) {
        return requests.get(id);
    }

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Approve a pending request (direct manager, or HR/ADMIN override)")
    LeaveRequestResponse approve(@PathVariable UUID id,
                                 @Valid @RequestBody(required = false) DecisionBody body) {
        return requests.approve(id, body == null ? null : body.decisionNote());
    }

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Reject a pending request; decisionNote is required")
    LeaveRequestResponse reject(@PathVariable UUID id,
                                @Valid @RequestBody(required = false) DecisionBody body) {
        return requests.reject(id, body == null ? null : body.decisionNote());
    }

    @PostMapping("/requests/{id}/cancel")
    @Operation(summary = "Cancel your own request: while PENDING, or while APPROVED and not yet started")
    LeaveRequestResponse cancel(@PathVariable UUID id,
                                @Valid @RequestBody(required = false) DecisionBody body) {
        return requests.cancel(id, body == null ? null : body.decisionNote());
    }

    // ------------------------------------------------------------------ calendar and accrual

    @GetMapping("/calendar")
    @Operation(summary = "Approved leave of the caller's team in a range (HR/ADMIN: everyone). Never includes reasons.")
    List<LeaveCalendarEntry> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendar.calendar(from, to);
    }

    @PostMapping("/accrual/run")
    @Operation(summary = "Run the monthly ANNUAL accrual (HR/ADMIN); idempotent per employee, year and month")
    AccrualRunResponse runAccrual(@RequestParam(required = false) Integer year,
                                  @RequestParam(required = false) Integer month) {
        return accrual.runOnDemand(year, month);
    }
}
