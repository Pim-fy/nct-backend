package nct.auction.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionListRequest;
import nct.auction.dto.AuctionListResponse;
import nct.auction.dto.AuctionStatusResponse;
import nct.auction.service.AuctionService;
import nct.global.response.ApiResponse;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping
    public ApiResponse<AuctionListResponse> findAuctions(@ModelAttribute AuctionListRequest request) {
        return ApiResponse.success(auctionService.findAuctions(request));
    }

    // F-AUC-006 판매자 경매 현황 조회 — 상품번호로 해당 경매 상태 반환
    @GetMapping("/product/{prdSn}")
    public ResponseEntity<ApiResponse<AuctionStatusResponse>> getAuctionStatusByProduct(
            @PathVariable Long prdSn) {
        return ResponseEntity.ok(ApiResponse.success(auctionService.getAuctionStatusByProduct(prdSn)));
    }
}
