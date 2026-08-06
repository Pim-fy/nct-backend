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
import nct.trade.dto.ServiceTradeCompletionRequest;
import nct.trade.dto.ServiceTradeDetailResponse;
import nct.trade.dto.ServiceTradeDisputeRequest;
import nct.trade.dto.ServiceTradeListPageResponse;
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
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.getMyMaterialTrades(userId, role, status, keyword)));
    }

    /** 의뢰자·제공자가 본인 서비스 거래를 조회해 서비스 거래 상세로 이동한다. */
    @GetMapping("/service")
    public ResponseEntity<ApiResponse<ServiceTradeListPageResponse>> getMyServiceTrades(
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.getMyServiceTrades(userId, role, status, keyword, page, size)));
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
            @PathVariable(name = "tradeId") long tradeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.getMyMaterialTradeDetail(tradeId, userId)));
    }

    /** 서비스 거래 당사자만 역할별 서비스 거래 상세를 조회한다. */
    @GetMapping("/{tradeId}/service-detail")
    public ResponseEntity<ApiResponse<ServiceTradeDetailResponse>> getMyServiceTradeDetail(
            @PathVariable(name = "tradeId") long tradeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.getMyServiceTradeDetail(tradeId, userId)));
    }

    /** 판매자가 본인 직거래의 일시·장소·상세 주소를 저장한다. */
    @PutMapping("/{tradeId}/offline-schedule")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> saveMyOfflineSchedule(
            @PathVariable(name = "tradeId") long tradeId,
            @Valid @RequestBody TradeOfflineScheduleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long sellerUserId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.saveMyOfflineSchedule(tradeId, sellerUserId, request)));
    }

    /** 판매자가 올린 인증사진과 메모를 하나의 발송 처리로 확정한다. */
    @PostMapping("/{tradeId}/delivery-proofs")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> submitDeliveryProof(
            @PathVariable(name = "tradeId") long tradeId,
            @Valid @RequestBody TradeDeliveryProofSubmitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long sellerUserId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.submitDeliveryProof(tradeId, sellerUserId, request)));
    }

    /** 구매자 또는 판매자가 거래 완료를 확인한다. 상대방 확인까지 끝나면 즉시 완료된다. */
    @PostMapping("/{tradeId}/completion-requests")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> requestCompletionConfirmation(
            @PathVariable(name = "tradeId") long tradeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                tradeService.requestCompletionConfirmation(tradeId, userId)));
    }

    /** 서비스 거래 당사자가 거래 문제를 접수하면 거래·정산을 같은 트랜잭션으로 보류한다. */
    @PostMapping("/{tradeId}/service-disputes")
    public ResponseEntity<ApiResponse<Void>> registerServiceTradeDispute(
            @PathVariable(name = "tradeId") long tradeId,
            @Valid @RequestBody ServiceTradeDisputeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        tradeService.registerServiceTradeDispute(tradeId, userDetails.getMember().getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 서비스 제공자가 완료 요청을 등록하고 의뢰자 확인 기한을 시작한다. */
    @PostMapping("/{tradeId}/service-completion-requests")
    public ResponseEntity<ApiResponse<Void>> requestServiceCompletion(
            @PathVariable(name = "tradeId") long tradeId,
            @Valid @RequestBody ServiceTradeCompletionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        tradeService.requestServiceCompletion(
                tradeId, userDetails.getMember().getId(), request.getCompletionMemo());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 서비스 의뢰자가 완료를 확인하면 정산과 정산가능 포인트 적립을 함께 처리한다. */
    @PostMapping("/{tradeId}/service-completions")
    public ResponseEntity<ApiResponse<Void>> confirmServiceCompletion(
            @PathVariable(name = "tradeId") long tradeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        tradeService.confirmServiceCompletion(tradeId, userDetails.getMember().getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
