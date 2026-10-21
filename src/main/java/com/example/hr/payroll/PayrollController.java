package com.example.hr.payroll;

import com.example.hr.employee.dto.PageResponse;
import com.example.hr.payroll.PayrollDtos.CreateRunBody;
import com.example.hr.payroll.PayrollDtos.PayrollRunDetailResponse;
import com.example.hr.payroll.PayrollDtos.PayrollRunResponse;
import com.example.hr.payroll.PayrollDtos.PayslipResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payroll")
@Tag(name = "Payroll")
class PayrollController {

    private final PayrollService payroll;
    private final PayslipPdfWriter pdf;
    private final PayrollExcelExporter excel;

    PayrollController(PayrollService payroll, PayslipPdfWriter pdf, PayrollExcelExporter excel) {
        this.payroll = payroll;
        this.pdf = pdf;
        this.excel = excel;
    }

    // ------------------------------------------------------------------ runs

    @PostMapping("/runs")
    @Operation(summary = "HR/ADMIN: create the run for a month and snapshot every payslip; "
            + "idempotent — a second call for the same month is 409 and creates nothing")
    ResponseEntity<PayrollRunDetailResponse> create(@Valid @RequestBody CreateRunBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payroll.createRun(body));
    }

    @GetMapping("/runs")
    @Operation(summary = "HR/ADMIN: the runs, newest first, paged")
    PageResponse<PayrollRunResponse> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return payroll.listRuns(page, size);
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "HR/ADMIN: one run with its totals and every payslip")
    PayrollRunDetailResponse get(@PathVariable UUID runId) {
        return payroll.getRun(runId);
    }

    @PostMapping("/runs/{runId}/recalculate")
    @Operation(summary = "HR/ADMIN: replace the payslips of a DRAFT run; 409 once FINALIZED")
    PayrollRunDetailResponse recalculate(@PathVariable UUID runId) {
        return payroll.recalculate(runId);
    }

    @PostMapping("/runs/{runId}/finalize")
    @Operation(summary = "HR/ADMIN: lock the run; afterwards recalculate and delete are 409")
    PayrollRunResponse finalizeRun(@PathVariable UUID runId) {
        return payroll.finalizeRun(runId);
    }

    @DeleteMapping("/runs/{runId}")
    @Operation(summary = "HR/ADMIN: delete a DRAFT run and its payslips; 409 once FINALIZED")
    ResponseEntity<Void> delete(@PathVariable UUID runId) {
        payroll.deleteRun(runId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/runs/{runId}/export.xlsx", produces = PayrollExcelExporter.XLSX_MEDIA_TYPE)
    @Operation(summary = "HR/ADMIN: the payroll register as .xlsx, with a SUM totals row")
    ResponseEntity<byte[]> exportRun(@PathVariable UUID runId) {
        byte[] workbook = excel.export(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("payroll-" + runId + ".xlsx").build().toString())
                .contentType(MediaType.parseMediaType(PayrollExcelExporter.XLSX_MEDIA_TYPE))
                .body(workbook);
    }

    // ------------------------------------------------------------------ payslips

    @GetMapping("/payslips/me")
    @Operation(summary = "Your own payslips — FINALIZED runs only; a draft is never visible")
    List<PayslipResponse> myPayslips() {
        return payroll.myPayslips();
    }

    @GetMapping("/payslips/{payslipId}")
    @Operation(summary = "One payslip: the employee themself once FINALIZED, HR/ADMIN always. "
            + "A manager never sees a report's payslip — 404.")
    PayslipResponse payslip(@PathVariable UUID payslipId) {
        return payroll.getPayslip(payslipId);
    }

    @GetMapping(value = "/payslips/{payslipId}/pdf", produces = PayslipPdfWriter.PDF_MEDIA_TYPE)
    @Operation(summary = "The same payslip as a PDF, with the same access rules")
    ResponseEntity<byte[]> payslipPdf(@PathVariable UUID payslipId) {
        PayslipResponse slip = payroll.getPayslip(payslipId);
        byte[] document = pdf.write(slip);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("payslip-%s-%d-%02d.pdf".formatted(slip.employeeNumber(), slip.year(),
                                slip.month())).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(document);
    }
}
