package nct.ops.servicequery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.ops.operation.dto.AdminOperationReasonRequest;
import nct.ops.operation.dto.AdminVisibilityChangeRequest;
import nct.ops.servicequery.dto.AdminQuoteOperationResponse;
import nct.ops.servicequery.dto.AdminServiceRequestListRequest;
import nct.ops.servicequery.dto.AdminServiceRequestDetailResponse;
import nct.ops.servicequery.dto.AdminServiceRequestPageResponse;
import nct.ops.servicequery.dto.AdminServiceRequestOperationResponse;
import nct.ops.servicequery.dto.AdminServiceRequestVisibilityResponse;
import nct.ops.servicequery.service.AdminServiceRequestOperationService;
import nct.ops.servicequery.service.AdminServiceRequestQueryService;

/** 담당자 7 · 관리자 42: ROLE_ADMIN 전용 서비스 요청 목록·상세 조회 API다. */
@RestController
@RequestMapping("/api/admin/service-requests")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminServiceRequestQueryController {
    private final AdminServiceRequestQueryService service;
    private final AdminServiceRequestOperationService operationService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminServiceRequestPageResponse>> getPage(
            @ModelAttribute AdminServiceRequestListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getPage(request)));
    }

    @GetMapping("/{serviceRequestId}")
    public ResponseEntity<ApiResponse<AdminServiceRequestDetailResponse>> getDetail(
            @PathVariable(name = "serviceRequestId") Long serviceRequestId) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(serviceRequestId)));
    }

    @PostMapping("/{serviceRequestId}/cancel")
    public ResponseEntity<ApiResponse<AdminServiceRequestOperationResponse>> cancelRequest(
            @PathVariable(name = "serviceRequestId") Long serviceRequestId,
            @Valid @RequestBody AdminOperationReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(operationService.cancelRequest(
                serviceRequestId,
                request.reason(),
                request.requestId(),
                actorId(userDetails))));
    }

    @PostMapping("/{serviceRequestId}/quotes/{quoteId}/invalidate")
    public ResponseEntity<ApiResponse<AdminQuoteOperationResponse>> invalidateQuote(
            @PathVariable(name = "serviceRequestId") Long serviceRequestId,
            @PathVariable(name = "quoteId") Long quoteId,
            @Valid @RequestBody AdminOperationReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(operationService.invalidateQuote(
                serviceRequestId,
                quoteId,
                request.reason(),
                request.requestId(),
                actorId(userDetails))));
    }

    @PostMapping("/{serviceRequestId}/visibility")
    public ResponseEntity<ApiResponse<AdminServiceRequestVisibilityResponse>> changeVisibility(
            @PathVariable(name = "serviceRequestId") Long serviceRequestId,
            @Valid @RequestBody AdminVisibilityChangeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(operationService.changeVisibility(
                serviceRequestId,
                request.visible(),
                request.reason(),
                request.requestId(),
                actorId(userDetails))));
    }

    private Long actorId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null
                || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
