package com.example.hr.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Read-only access to the seeded {@code leave_types} reference table. */
public interface LeaveTypeRepository extends JpaRepository<LeaveTypeDefinition, LeaveType> {

    List<LeaveTypeDefinition> findAllByOrderByCodeAsc();
}
