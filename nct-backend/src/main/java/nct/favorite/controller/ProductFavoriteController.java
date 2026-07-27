package nct.favorite.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.favorite.dto.FavoriteAuctionListResponse;
import nct.favorite.dto.FavoriteStatusResponse;
import nct.favorite.service.ProductFavoriteService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class ProductFavoriteController {

    private final ProductFavoriteService productFavoriteService;

    @PutMapping("/{auctionId}/favorite")
    public ApiResponse<FavoriteStatusResponse> addFavorite(
            @PathVariable("auctionId") Long auctionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(productFavoriteService.addFavorite(auctionId, currentUserId(userDetails)));
    }

    @DeleteMapping("/{auctionId}/favorite")
    public ApiResponse<FavoriteStatusResponse> removeFavorite(
            @PathVariable("auctionId") Long auctionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(productFavoriteService.removeFavorite(auctionId, currentUserId(userDetails)));
    }

    @GetMapping("/{auctionId}/favorite")
    public ApiResponse<FavoriteStatusResponse> getFavoriteStatus(
            @PathVariable("auctionId") Long auctionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(productFavoriteService.getFavoriteStatus(auctionId, currentUserId(userDetails)));
    }

    @GetMapping("/favorites/me")
    public ApiResponse<FavoriteAuctionListResponse> getMyFavorites(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "12") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(productFavoriteService.getMyFavorites(currentUserId(userDetails), page, size));
    }

    private Long currentUserId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
