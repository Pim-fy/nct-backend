package nct.abuse.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.abuse.dto.CustomerAbuseReportRequest;
import nct.abuse.dto.ManualAbuseReportRequest;
import nct.abuse.dto.ManualAbuseReportResponse;
import nct.abuse.dto.ManualAbuseReportStatusResponse;
import nct.abuse.dto.MyAbuseReportResponse;
import nct.abuse.service.AbuseReportService;
import nct.global.response.ApiResponse;
import nct.global.response.PageResponse;
import nct.global.security.domain.CustomUserDetails;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/abuse-reports")
public class AbuseReportController {

    private final AbuseReportService abuseReportService;

    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ResponseEntity<ApiResponse<ManualAbuseReportResponse>> createManualReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ManualAbuseReportRequest request) {

        Long reporterUserSn = userDetails.getMember().getId();
        return ResponseEntity.status(201).body(ApiResponse.created(
                abuseReportService.requestManualReport(reporterUserSn, request)));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/customer")
    public ResponseEntity<ApiResponse<ManualAbuseReportResponse>> submitCustomerReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CustomerAbuseReportRequest request) {

        Long reporterUserSn = userDetails.getMember().getId();
        return ResponseEntity.status(201).body(ApiResponse.created(
                abuseReportService.submitCustomerReport(reporterUserSn, request)));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<MyAbuseReportResponse>>> getMyReports(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        Long reporterUserSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                abuseReportService.getMyReports(reporterUserSn, status, page, size)));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/{reportSn}")
    public ResponseEntity<ApiResponse<MyAbuseReportResponse>> getMyReportDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "reportSn") Long reportSn) {

        Long reporterUserSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                abuseReportService.getMyReportDetail(reporterUserSn, reportSn)));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/references")
    public ResponseEntity<ApiResponse<List<ManualAbuseReportStatusResponse>>> getMyReportReferences(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "referenceTypeCode") String referenceTypeCode) {

        Long reporterUserSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                abuseReportService.getMyManualReportReferences(
                        reporterUserSn,
                        referenceTypeCode)));
    }

    @GetMapping("/references/statuses")
    public ResponseEntity<ApiResponse<List<ManualAbuseReportStatusResponse>>> getActiveReportReferences(
            @RequestParam(name = "referenceTypeCode") String referenceTypeCode,
            @RequestParam(name = "referenceSns") List<Long> referenceSns) {

        return ResponseEntity.ok(ApiResponse.success(
                abuseReportService.getActiveManualReportReferences(
                        referenceTypeCode,
                        referenceSns)));
    }
}
