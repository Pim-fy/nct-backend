package nct.ops.operation.controller;

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
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.auction.dto.AuctionPendingCancelRequestResponse;
import nct.ops.operation.dto.AdminSellerCancellationDecisionRequest;
import nct.ops.operation.service.AdminSellerCancellationService;

/**
 * 담당자 7 · F-OPS-004: 관리자 판매자 취소 승인/반려 API입니다.
 * 실제 거래 상태 변경은 담당자 4의 SellerCancellationDecisionPort 구현체에 위임합니다.
 */
@RestController
@RequestMapping("/api/admin/seller-cancellations")
@RequiredArgsConstructor
public class AdminSellerCancellationController {

    private final AdminSellerCancellationService adminSellerCancellationService;

    @GetMapping("/auctions/{aucSn}/cancellation-request")
    public ResponseEntity<ApiResponse<AuctionPendingCancelRequestResponse>> getPendingAuctionCancellationRequest(
            @PathVariable(name = "aucSn") Long aucSn) {
        return ResponseEntity.ok(ApiResponse.success(
                adminSellerCancellationService.getPendingAuctionCancellationRequest(aucSn)));
    }

    @PostMapping("/auctions/{aucSn}/cancellation-request/decision")
    public ResponseEntity<ApiResponse<Void>> decideAuctionCancellation(
            @PathVariable(name = "aucSn") Long aucSn,
            @Valid @RequestBody AdminSellerCancellationDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        adminSellerCancellationService.decideAuctionCancellation(
                aucSn,
                request.getDecision(),
                request.getReason(),
                userId(userDetails));

        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{tradeSn}/decision")
    public ResponseEntity<ApiResponse<Void>> decide(
            @PathVariable(name = "tradeSn") Long tradeSn,
            @Valid @RequestBody AdminSellerCancellationDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long adminUserId = userId(userDetails);
        adminSellerCancellationService.decide(
                tradeSn,
                request.getDecision(),
                request.getReason(),
                adminUserId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    private Long userId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
