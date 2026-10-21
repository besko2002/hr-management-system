package com.example.hr.leave;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.leave.LeaveDtos.LeaveTypeResponse;
import com.example.hr.security.CurrentEmployee;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only view of the seeded leave types. HR/ADMIN only — it is internal policy data. */
@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypes;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;

    LeaveTypeService(LeaveTypeRepository leaveTypes, EmployeeAccessPolicy policy, CurrentEmployee currentEmployee) {
        this.leaveTypes = leaveTypes;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> list() {
        policy.requireHrOrAdmin(currentEmployee.require(), "read the leave-type configuration");
        return leaveTypes.findAllByOrderByCodeAsc().stream().map(LeaveMapper::toResponse).toList();
    }
}
