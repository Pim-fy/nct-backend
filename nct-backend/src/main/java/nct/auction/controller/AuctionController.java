package nct.auction.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionCancelRequest;
import nct.auction.dto.AuctionListRequest;
import nct.auction.dto.AuctionListResponse;
import nct.auction.service.AuctionCancellationService;
import nct.auction.service.AuctionService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;
    private final AuctionCancellationService auctionCancellationService;

    @GetMapping
    public ApiResponse<AuctionListResponse> findAuctions(@ModelAttribute AuctionListRequest request) {
        return ApiResponse.success(auctionService.findAuctions(request));
    }

    @PostMapping("/{aucSn}/cancel-request")
    public ApiResponse<Void> requestCancellation(
            @PathVariable(name = "aucSn") Long aucSn,
            @Valid @RequestBody AuctionCancelRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        auctionCancellationService.requestCancellation(aucSn, currentUserId(userDetails), request.reason());
        return ApiResponse.success();
    }

    private Long currentUserId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
