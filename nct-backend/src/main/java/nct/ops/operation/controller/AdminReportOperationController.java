package nct.ops.operation.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.ops.operation.dto.AdminReportDecisionRequest;
import nct.ops.operation.service.AdminReportOperationService;

/** 담당자 7 · F-OPS-007: 관리자 신고 처리·반려 API입니다. */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportOperationController {

    private final AdminReportOperationService adminReportOperationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminAbuseReportResponse>>> getPendingReports() {
        return ResponseEntity.ok(ApiResponse.success(adminReportOperationService.getPendingReports()));
    }

    @GetMapping("/{reportSn}")
    public ResponseEntity<ApiResponse<AdminAbuseReportResponse>> getReportDetail(
            @PathVariable(name = "reportSn") Long reportSn) {
        return ResponseEntity.ok(ApiResponse.success(adminReportOperationService.getReportDetail(reportSn)));
    }

    @PostMapping("/{reportSn}/decision")
    public ResponseEntity<ApiResponse<Void>> decide(
            @PathVariable(name = "reportSn") Long reportSn,
            @Valid @RequestBody AdminReportDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        adminReportOperationService.decide(
                reportSn,
                request.getDecision(),
                request.getReason(),
                userId(userDetails));
        return ResponseEntity.ok(ApiResponse.success());
    }

    private Long userId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
