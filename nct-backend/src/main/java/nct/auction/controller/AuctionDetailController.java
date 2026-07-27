package nct.auction.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.dto.AuctionStatusResponse;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.auction.service.AuctionService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionDetailController {

    private final AuctionService auctionService;

    @GetMapping("/{auctionId}")
    public ApiResponse<AuctionDetailResponse> findAuctionDetail(
            @PathVariable("auctionId") Long auctionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(auctionService.findAuctionDetail(auctionId, optionalUserId(userDetails)));
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<AuctionStatusResponse> getAuctionStatusByProduct(
            @PathVariable("productId") Long productId) {
        return ApiResponse.success(auctionService.getAuctionStatusByProduct(productId));
    }

    @PostMapping("/{auctionId}/bids")
    public ApiResponse<AuctionDetailResponse> placeBid(
            @PathVariable("auctionId") Long auctionId,
            @RequestBody AuctionBidRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(auctionService.placeBid(auctionId, currentUserId(userDetails), request));
    }

    @PostMapping("/{auctionId}/buy-now")
    public ApiResponse<AuctionDetailResponse> buyNow(
            @PathVariable("auctionId") Long auctionId,
            @RequestBody AuctionBuyNowRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(auctionService.buyNow(auctionId, currentUserId(userDetails), request));
    }

    private Long currentUserId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }

    private Long optionalUserId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            return null;
        }
        return userDetails.getMember().getId();
    }
}
