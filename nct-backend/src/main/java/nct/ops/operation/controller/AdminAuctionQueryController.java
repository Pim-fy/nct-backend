package nct.ops.operation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.operation.dto.AdminAuctionListRequest;
import nct.ops.operation.dto.AdminAuctionPageResponse;
import nct.ops.operation.dto.AdminAuctionOverviewResponse;
import nct.ops.operation.service.AdminAuctionQueryService;

/** 담당자 7 · F-OPS-003: ROLE_ADMIN 전용 경매 운영 조회 API입니다. */
@RestController
@RequestMapping("/api/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionQueryController {
    private final AdminAuctionQueryService service;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminAuctionPageResponse>> getPage(@ModelAttribute AdminAuctionListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getPage(request)));
    }

    @GetMapping("/{auctionSn}")
    public ResponseEntity<ApiResponse<AdminAuctionOverviewResponse>> getAuctionOverview(
            @PathVariable(name = "auctionSn") Long auctionSn) {
        return ResponseEntity.ok(ApiResponse.success(service.getAuctionOverview(auctionSn)));
    }
}
