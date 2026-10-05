package com.example.hr.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    Optional<PayrollRun> findByYearAndMonth(int year, int month);

    boolean existsByYearAndMonth(int year, int month);

    Page<PayrollRun> findAllByOrderByYearDescMonthDesc(Pageable pageable);
}
