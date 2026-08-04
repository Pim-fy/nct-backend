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

    // PublicProviderProfilePage.jsx의 신고 버튼이 role 제한 없이 렌더링되어, 제공자 모드
    // (ROLE_SERVICE)에서 다른 제공자를 신고할 때도 이 API를 호출한다. USR_ROLE_CD는 단일
    // 값이라 제공자 모드에서는 ROLE_USER 권한이 없어 hasRole('USER')만으로는 403이 발생했다.
    @PreAuthorize("hasAnyRole('USER','SERVICE')")
    @PostMapping("/customer")
    public ResponseEntity<ApiResponse<ManualAbuseReportResponse>> submitCustomerReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CustomerAbuseReportRequest request) {

        Long reporterUserSn = userDetails.getMember().getId();
        return ResponseEntity.status(201).body(ApiResponse.created(
                abuseReportService.submitCustomerReport(reporterUserSn, request)));
    }

    // 마이페이지 사이드바의 "내 신고 목록"이 제공자 모드 메뉴에도 있어(MyPageSidebar.jsx
    // PROVIDER_MENU_ITEMS), 일반회원일 때 넣은 신고를 제공자 모드에서도 조회할 수 있어야 한다.
    @PreAuthorize("hasAnyRole('USER','SERVICE')")
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

    // 목록과 같은 이유로 제공자 모드에서도 조회 허용
    @PreAuthorize("hasAnyRole('USER','SERVICE')")
    @GetMapping("/me/{reportSn}")
    public ResponseEntity<ApiResponse<MyAbuseReportResponse>> getMyReportDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long reportSn) {

        Long reporterUserSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                abuseReportService.getMyReportDetail(reporterUserSn, reportSn)));
    }

    // 목록과 같은 이유로 제공자 모드에서도 조회 허용
    @PreAuthorize("hasAnyRole('USER','SERVICE')")
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
