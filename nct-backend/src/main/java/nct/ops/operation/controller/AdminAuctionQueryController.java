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
import nct.auction.port.AdminAuctionCancellationResult;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.ops.operation.dto.AdminAuctionListRequest;
import nct.ops.operation.dto.AdminAuctionPageResponse;
import nct.ops.operation.dto.AdminAuctionOverviewResponse;
import nct.ops.operation.dto.AdminOperationReasonRequest;
import nct.ops.operation.service.AdminAuctionOperationService;
import nct.ops.operation.service.AdminAuctionQueryService;

/** 담당자 7 · F-OPS-003: ROLE_ADMIN 전용 경매 운영 조회 API입니다. */
@RestController
@RequestMapping("/api/admin/auctions")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminAuctionQueryController {
    private final AdminAuctionQueryService service;
    private final AdminAuctionOperationService operationService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminAuctionPageResponse>> getPage(@ModelAttribute AdminAuctionListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getPage(request)));
    }

    @GetMapping("/{auctionSn}")
    public ResponseEntity<ApiResponse<AdminAuctionOverviewResponse>> getAuctionOverview(
            @PathVariable(name = "auctionSn") Long auctionSn) {
        return ResponseEntity.ok(ApiResponse.success(service.getAuctionOverview(auctionSn)));
    }

    @PostMapping("/{auctionSn}/force-cancel")
    public ResponseEntity<ApiResponse<AdminAuctionCancellationResult>> forceCancel(
            @PathVariable(name = "auctionSn") Long auctionSn,
            @Valid @RequestBody AdminOperationReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(operationService.forceCancel(
                auctionSn,
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
