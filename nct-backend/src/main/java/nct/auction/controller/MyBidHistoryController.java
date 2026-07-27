package nct.auction.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.MyBidHistoryItem;
import nct.auction.service.BidService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

@RestController
@RequestMapping("/api/bids")
@RequiredArgsConstructor
public class MyBidHistoryController {

    private final BidService bidService;

    @GetMapping("/me")
    public ApiResponse<List<MyBidHistoryItem>> getMyBidHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(bidService.getMyBidHistory(userDetails.getMember().getId()));
    }
}
