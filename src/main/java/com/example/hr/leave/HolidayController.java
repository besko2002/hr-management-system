package com.example.hr.leave;

import com.example.hr.leave.LeaveDtos.HolidayRequestBody;
import com.example.hr.leave.LeaveDtos.HolidayResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave/holidays")
@Tag(name = "Leave")
class HolidayController {

    private final HolidayService holidays;

    HolidayController(HolidayService holidays) {
        this.holidays = holidays;
    }

    @GetMapping
    @Operation(summary = "List holidays (any authenticated user), optionally inside a range")
    List<HolidayResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return holidays.list(from, to);
    }

    @PostMapping
    @Operation(summary = "Add a holiday (HR/ADMIN); 409 when the date is already a holiday")
    ResponseEntity<HolidayResponse> create(@Valid @RequestBody HolidayRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(holidays.create(body));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Move or rename a holiday (HR/ADMIN)")
    HolidayResponse update(@PathVariable UUID id, @Valid @RequestBody HolidayRequestBody body) {
        return holidays.update(id, body);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a holiday (HR/ADMIN)")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        holidays.delete(id);
        return ResponseEntity.noContent().build();
    }
}
