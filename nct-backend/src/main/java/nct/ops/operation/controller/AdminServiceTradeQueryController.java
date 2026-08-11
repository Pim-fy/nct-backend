package nct.ops.operation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.operation.service.AdminServiceTradeQueryService;
import nct.trade.dto.ServiceTradeDetailResponse;

/** 담당자 7 · F-OPS-005: ROLE_ADMIN 전용 서비스 거래 상세 조회 API입니다. */
@RestController
@RequestMapping("/api/admin/service-trades")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminServiceTradeQueryController {

    private final AdminServiceTradeQueryService service;

    @GetMapping("/{tradeId}")
    public ResponseEntity<ApiResponse<ServiceTradeDetailResponse>> getDetail(
            @PathVariable(name = "tradeId") Long tradeId) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(tradeId)));
    }
}
