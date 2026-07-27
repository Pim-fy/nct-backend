package nct.ops.operation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.operation.dto.AdminAuctionOverviewResponse;
import nct.ops.operation.service.AdminAuctionQueryService;

/** 담당자 7 · F-OPS-003: 관리자 경매 운영 상세 조회 API입니다. */
@RestController
@RequestMapping("/api/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionQueryController {

    private final AdminAuctionQueryService adminAuctionQueryService;

    @GetMapping("/{auctionSn}")
    public ResponseEntity<ApiResponse<AdminAuctionOverviewResponse>> getAuctionOverview(
            @PathVariable(name = "auctionSn") Long auctionSn) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAuctionQueryService.getAuctionOverview(auctionSn)));
    }
}
