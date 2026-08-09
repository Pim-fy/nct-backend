package nct.ops.operation.controller;

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
import nct.ops.operation.dto.AdminDisputeDecisionRequest;
import nct.ops.operation.dto.AdminDisputeDecisionResponse;
import nct.ops.operation.dto.AdminDisputeDetailResponse;
import nct.ops.operation.dto.AdminDisputeListRequest;
import nct.ops.operation.dto.AdminDisputePageResponse;
import nct.ops.operation.service.AdminDisputeDecisionService;
import nct.ops.operation.service.AdminDisputeQueryService;

/** 담당자 7 · F-OPS-005/006: ROLE_ADMIN 전용 거래 분쟁 조회·판정 API입니다. */
@RestController
@RequestMapping("/api/admin/disputes")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminDisputeQueryController {

    private final AdminDisputeQueryService service;
    private final AdminDisputeDecisionService decisionService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminDisputePageResponse>> getPage(
            @ModelAttribute AdminDisputeListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getPage(request)));
    }

    @GetMapping("/{disputeSn}")
    public ResponseEntity<ApiResponse<AdminDisputeDetailResponse>> getDetail(
            @PathVariable(name = "disputeSn") Long disputeSn) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(disputeSn)));
    }

    @PostMapping("/{disputeSn}/decision")
    public ResponseEntity<ApiResponse<AdminDisputeDecisionResponse>> decide(
            @PathVariable(name = "disputeSn") Long disputeSn,
            @Valid @RequestBody AdminDisputeDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(decisionService.decide(
                disputeSn,
                request.decision(),
                request.reason(),
                adminUserSn(userDetails))));
    }

    private Long adminUserSn(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
