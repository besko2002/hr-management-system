package com.example.hr.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findAllByOrderByDateAsc();

    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    Optional<Holiday> findByDate(LocalDate date);

    boolean existsByDate(LocalDate date);

    /** Only the dates, for the working-day calculation of one request. */
    @Query("select h.date from Holiday h where h.date between :from and :to")
    List<LocalDate> findDatesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
