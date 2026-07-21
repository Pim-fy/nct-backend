package nct.trade.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeDeliveryProofSubmitRequest;
import nct.trade.dto.TradeListItem;
import nct.trade.dto.TradeOfflineScheduleRequest;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.service.TradeService;

/** 로그인한 사용자의 물건 거래내역과 상세 조회 API다. */
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    /** 구매·판매 역할을 함께 포함한 내 물건 거래 목록을 조회한다. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TradeListItem>>> getMyTrades(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.getMyMaterialTrades(userId, role, status, keyword)));
    }

    /** F-AUC-005 판매 목록이 경매 상태와 결합할 판매자 본인의 거래 상태를 조회한다. */
    @GetMapping("/seller/status")
    public ResponseEntity<ApiResponse<List<SellerTradeStatusItem>>> getMySellerTradeStatuses(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long sellerUserId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.getMySellerTradeStatuses(sellerUserId)));
    }

    /** URL 거래번호와 로그인 사용자 정보를 함께 검증해 상세를 조회한다. */
    @GetMapping("/{tradeId}")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> getMyTradeDetail(
            @PathVariable("tradeId") long tradeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.getMyMaterialTradeDetail(tradeId, userId)));
    }

    /** 판매자가 본인 직거래의 일시·장소·상세 주소를 저장한다. */
    @PutMapping("/{tradeId}/offline-schedule")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> saveMyOfflineSchedule(
            @PathVariable("tradeId") long tradeId,
            @Valid @RequestBody TradeOfflineScheduleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long sellerUserId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.saveMyOfflineSchedule(tradeId, sellerUserId, request)));
    }

    /** 판매자가 올린 인증사진과 메모를 하나의 발송 처리로 확정한다. */
    @PostMapping("/{tradeId}/delivery-proofs")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> submitDeliveryProof(
            @PathVariable("tradeId") long tradeId,
            @Valid @RequestBody TradeDeliveryProofSubmitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long sellerUserId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.submitDeliveryProof(tradeId, sellerUserId, request)));
    }

    /** 구매자가 거래 완료를 확인하고 상대방 확인·자동완료 기한을 시작한다. */
    @PostMapping("/{tradeId}/completion-requests")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> requestCompletionConfirmation(
            @PathVariable("tradeId") long tradeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long buyerUserId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.requestCompletionConfirmation(tradeId, buyerUserId)));
    }
}
