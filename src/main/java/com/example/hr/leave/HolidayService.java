package com.example.hr.leave;

import com.example.hr.access.EmployeeAccessPolicy;
import com.example.hr.common.BadRequestException;
import com.example.hr.common.ConflictException;
import com.example.hr.common.ResourceNotFoundException;
import com.example.hr.leave.LeaveDtos.HolidayRequestBody;
import com.example.hr.leave.LeaveDtos.HolidayResponse;
import com.example.hr.security.CurrentEmployee;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public holidays. Any authenticated user may read the calendar (they need it to plan
 * leave); only HR and ADMIN may change it. The date is unique — a duplicate is a 409.
 */
@Service
public class HolidayService {

    private final HolidayRepository holidays;
    private final EmployeeAccessPolicy policy;
    private final CurrentEmployee currentEmployee;

    HolidayService(HolidayRepository holidays, EmployeeAccessPolicy policy, CurrentEmployee currentEmployee) {
        this.holidays = holidays;
        this.policy = policy;
        this.currentEmployee = currentEmployee;
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> list(LocalDate from, LocalDate to) {
        currentEmployee.require();
        if (from != null && to != null && to.isBefore(from)) {
            throw new BadRequestException("'to' must not be before 'from'");
        }
        List<Holiday> found = from == null && to == null
                ? holidays.findAllByOrderByDateAsc()
                : holidays.findByDateBetweenOrderByDateAsc(
                        from == null ? LocalDate.of(2000, 1, 1) : from,
                        to == null ? LocalDate.of(2100, 12, 31) : to);
        return found.stream().map(HolidayService::toResponse).toList();
    }

    @Transactional
    public HolidayResponse create(HolidayRequestBody request) {
        policy.requireHrOrAdmin(currentEmployee.require(), "manage holidays");
        if (holidays.existsByDate(request.date())) {
            throw new ConflictException("A holiday is already registered on " + request.date());
        }
        return toResponse(holidays.save(new Holiday(request.date(), request.name().trim())));
    }

    @Transactional
    public HolidayResponse update(UUID id, HolidayRequestBody request) {
        policy.requireHrOrAdmin(currentEmployee.require(), "manage holidays");
        Holiday holiday = require(id);
        if (!holiday.getDate().equals(request.date()) && holidays.existsByDate(request.date())) {
            throw new ConflictException("A holiday is already registered on " + request.date());
        }
        holiday.moveTo(request.date());
        holiday.rename(request.name().trim());
        return toResponse(holidays.save(holiday));
    }

    @Transactional
    public void delete(UUID id) {
        policy.requireHrOrAdmin(currentEmployee.require(), "manage holidays");
        holidays.delete(require(id));
    }

    /** Internal: the holiday dates inside one range, for the working-day calculation. */
    @Transactional(readOnly = true)
    public List<LocalDate> datesBetween(LocalDate from, LocalDate to) {
        return holidays.findDatesBetween(from, to);
    }

    private Holiday require(UUID id) {
        return holidays.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No holiday with id " + id));
    }

    private static HolidayResponse toResponse(Holiday holiday) {
        return new HolidayResponse(holiday.getId(), holiday.getDate(), holiday.getName());
    }
}
