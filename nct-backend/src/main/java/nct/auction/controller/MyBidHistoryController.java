package nct.auction.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nct.auction.dto.MyBidHistoryItem;
import nct.auction.service.BidService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * [내 입찰 내역 API]
 *
 *  GET /api/bids/me  내 입찰 내역 조회(F-AUC-022)  (authenticated)
 *
 * BidController 와 별도 컨트롤러로 분리한 이유: BidController 는 "특정 경매 하나에 대한 행동
 * (입찰/즉시구매)"을 다루는 `/api/auctions/{aucSn}/...` 자원이고, 이 API 는 "경매에 종속되지
 * 않는 내 전체 입찰 이력"이라 URL 로도 리소스 성격이 다르다는 것을 드러내는 게 자연스럽다.
 */
@RestController
@RequestMapping("/api/bids")
@RequiredArgsConstructor
public class MyBidHistoryController {

    private final BidService bidService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<MyBidHistoryItem>>> getMyBidHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long usrSn = userDetails.getMember().getId();
        List<MyBidHistoryItem> history = bidService.getMyBidHistory(usrSn);

        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
