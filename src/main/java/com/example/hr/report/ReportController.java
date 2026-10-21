package com.example.hr.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
@SecurityRequirement(name = "bearerAuth")
class ReportController {

    private final ReportService reports;
    private final ReportExcelExporter excel;

    ReportController(ReportService reports, ReportExcelExporter excel) {
        this.reports = reports;
        this.excel = excel;
    }

    @GetMapping("/headcount")
    @Operation(summary = "Headcount by department and status (HR/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aggregated headcount"),
            @ApiResponse(responseCode = "403", description = "Caller is not HR/ADMIN", content = @Content)
    })
    List<ReportDtos.HeadcountRow> headcount() {
        return reports.headcount();
    }

    @GetMapping("/headcount.xlsx")
    @Operation(summary = "Headcount report as Excel (HR/ADMIN)")
    ResponseEntity<byte[]> headcountExcel() {
        byte[] body = excel.exportHeadcount();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"headcount.xlsx\"")
                .contentType(MediaType.parseMediaType(ReportExcelExporter.XLSX_MEDIA_TYPE))
                .body(body);
    }

    @GetMapping("/leave-summary")
    @Operation(summary = "Leave used/pending/remaining by department and type (HR/ADMIN)")
    List<ReportDtos.LeaveSummaryRow> leaveSummary(@RequestParam int year) {
        return reports.leaveSummary(year);
    }

    @GetMapping("/payroll-summary")
    @Operation(summary = "Payroll totals per month from FINALIZED runs (HR/ADMIN)")
    List<ReportDtos.PayrollSummaryRow> payrollSummary(@RequestParam int year) {
        return reports.payrollSummary(year);
    }

    @GetMapping("/attendance-summary")
    @Operation(summary = "Late/absent/overtime per department for a month (HR/ADMIN)")
    List<ReportDtos.AttendanceSummaryRow> attendanceSummary(@RequestParam int year,
                                                            @RequestParam int month) {
        return reports.attendanceSummary(year, month);
    }
}
