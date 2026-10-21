package com.example.hr.attendance;

import com.example.hr.attendance.AttendanceDtos.AttendanceRangeResponse;
import com.example.hr.attendance.AttendanceDtos.CorrectSessionBody;
import com.example.hr.attendance.AttendanceDtos.SessionResponse;
import com.example.hr.attendance.AttendanceDtos.TeamTodayResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
@Tag(name = "Attendance")
class AttendanceController {

    private final AttendanceService attendance;
    private final AttendanceExcelExporter exporter;

    AttendanceController(AttendanceService attendance, AttendanceExcelExporter exporter) {
        this.attendance = attendance;
        this.exporter = exporter;
    }

    @PostMapping("/check-in")
    @Operation(summary = "Start a session for yourself; 409 if one is already open today")
    ResponseEntity<SessionResponse> checkIn() {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendance.checkIn());
    }

    @PostMapping("/check-out")
    @Operation(summary = "Close today's open session; 409 when there is none")
    SessionResponse checkOut() {
        return attendance.checkOut();
    }

    @GetMapping("/me")
    @Operation(summary = "Your own days and totals in a range of at most 92 days")
    AttendanceRangeResponse me(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendance.myDays(from, to);
    }

    @GetMapping("/employees/{employeeId}")
    @Operation(summary = "One employee's days: self, any ancestor manager, HR/ADMIN; 404 otherwise. Never any pay.")
    AttendanceRangeResponse ofEmployee(
            @PathVariable UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendance.daysOf(employeeId, from, to);
    }

    @GetMapping("/team/today")
    @Operation(summary = "Today for all your descendants (HR/ADMIN: everyone): in, late, absent, on leave")
    TeamTodayResponse teamToday() {
        return attendance.teamToday();
    }

    @PostMapping("/sessions/{sessionId}/correct")
    @Operation(summary = "HR/ADMIN: fix an existing session; reason required, overlaps 409")
    SessionResponse correct(@PathVariable UUID sessionId, @Valid @RequestBody CorrectSessionBody body) {
        return attendance.correct(sessionId, body);
    }

    @PostMapping("/employees/{employeeId}/sessions")
    @Operation(summary = "HR/ADMIN: add a missing session; reason required, overlaps 409")
    ResponseEntity<SessionResponse> addSession(@PathVariable UUID employeeId,
                                               @Valid @RequestBody CorrectSessionBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendance.addSession(employeeId, body));
    }

    @GetMapping(value = "/export.xlsx", produces = AttendanceExcelExporter.XLSX_MEDIA_TYPE)
    @Operation(summary = "HR/ADMIN: one row per employee-day of a month, as .xlsx")
    ResponseEntity<byte[]> export(@RequestParam int year, @RequestParam int month) {
        byte[] workbook = exporter.export(year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("attendance-%d-%02d.xlsx".formatted(year, month)).build().toString())
                .contentType(MediaType.parseMediaType(AttendanceExcelExporter.XLSX_MEDIA_TYPE))
                .body(workbook);
    }
}
