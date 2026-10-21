package com.example.hr.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    @Query("""
            select p from Payslip p
              join fetch p.employee e
              join fetch p.run r
             where r.id = :runId
             order by p.employeeNumber
            """)
    List<Payslip> findByRun(@Param("runId") UUID runId);

    /** The employee's own payslips, FINALIZED runs only — a draft must never leak. */
    @Query("""
            select p from Payslip p
              join fetch p.run r
             where p.employee.id = :employeeId
               and r.status = com.example.hr.payroll.PayrollRunStatus.FINALIZED
             order by r.year desc, r.month desc
            """)
    List<Payslip> findFinalizedOf(@Param("employeeId") UUID employeeId);

    @Modifying
    @Query("delete from Payslip p where p.run.id = :runId")
    int deleteByRunId(@Param("runId") UUID runId);

    long countByRunId(UUID runId);
}
