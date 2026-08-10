package nct.settlement.controller;

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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.settlement.dto.AdminSettlementActionRequest;
import nct.settlement.dto.AdminSettlementActionResponse;
import nct.settlement.dto.AdminSettlementDetailResponse;
import nct.settlement.dto.AdminSettlementListRequest;
import nct.settlement.dto.AdminSettlementPageResponse;
import nct.settlement.service.AdminSettlementService;

/** F-OPS-009 ROLE_ADMIN 전용 정산 목록·상세·보류·해제 API입니다. */
@RestController
@RequestMapping("/api/admin/settlements")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final AdminSettlementService service;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminSettlementPageResponse>> getPage(
            @ModelAttribute AdminSettlementListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getPage(request)));
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<ApiResponse<AdminSettlementDetailResponse>> getDetail(
            @PathVariable(name = "settlementId") Long settlementId) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(settlementId)));
    }

    @PostMapping("/{settlementId}/hold")
    public ResponseEntity<ApiResponse<AdminSettlementActionResponse>> hold(
            @PathVariable(name = "settlementId") Long settlementId,
            @Valid @RequestBody AdminSettlementActionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(service.hold(
                settlementId,
                request.reason(),
                request.requestId(),
                adminUserId(userDetails))));
    }

    @PostMapping("/{settlementId}/release")
    public ResponseEntity<ApiResponse<AdminSettlementActionResponse>> release(
            @PathVariable(name = "settlementId") Long settlementId,
            @Valid @RequestBody AdminSettlementActionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(service.release(
                settlementId,
                request.reason(),
                request.requestId(),
                adminUserId(userDetails))));
    }

    private Long adminUserId(CustomUserDetails userDetails) {
        if (userDetails == null
                || userDetails.getMember() == null
                || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
