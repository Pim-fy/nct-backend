package nct.trade.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeListItem;
import nct.trade.dto.TradeOfflineScheduleRequest;
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
}
