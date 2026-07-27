package nct.point.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.point.dto.AdminExchangeRejectRequest;
import nct.point.dto.AdminPointExchangeOrderResponse;
import nct.point.service.PointExchangeService;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 환전 - 관리자 처리 REST 컨트롤러] (F-PAY-012, D-026)
 *
 * 엔드포인트 (전부 관리자 전용 — /api/admin/**는 SecurityConfig에서 ROLE_ADMIN만 통과):
 *   GET  /api/admin/point/exchange/orders          처리 대기(신청) 목록 — 오래된 순
 *   POST /api/admin/point/exchange/{번호}/complete  지급 완료 처리 (실제 이체를 마친 뒤)
 *   POST /api/admin/point/exchange/{번호}/reject    반려 처리 (사유 필수, 포인트 자동 복원)
 *
 * 실제 계좌 이체는 시스템 밖(관리자 수동)에서 일어난다 — 지급·승인 자동화 금지 정본 규칙.
 * 이 API는 그 수동 처리의 "결과 기록"만 담당한다.
 */
@RestController
@RequestMapping("/api/admin/point/exchange")
@RequiredArgsConstructor
public class AdminPointExchangeController {

    private final PointExchangeService pointExchangeService;

    /** 처리 대기 목록 — 관리자가 "누구에게 어디로 얼마" 보낼지 보는 화면의 데이터 */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<AdminPointExchangeOrderResponse>>> getRequestedOrders() {
        List<AdminPointExchangeOrderResponse> body = pointExchangeService.getRequestedListForAdmin().stream()
                .map(AdminPointExchangeOrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 지급 완료 처리 — 처리자(관리자)는 인증 토큰에서 꺼낸다 */
    @PostMapping("/{ptExcOrdSn}/complete")
    public ResponseEntity<ApiResponse<Void>> complete(
            @PathVariable(name = "ptExcOrdSn") long ptExcOrdSn,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long adminUsrSn = userDetails.getMember().getId();
        pointExchangeService.complete(ptExcOrdSn, adminUsrSn);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 반려 처리 — 사유 필수, 차감 포인트는 자동 복원된다 */
    @PostMapping("/{ptExcOrdSn}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable(name = "ptExcOrdSn") long ptExcOrdSn,
            @Valid @RequestBody AdminExchangeRejectRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long adminUsrSn = userDetails.getMember().getId();
        pointExchangeService.reject(ptExcOrdSn, adminUsrSn, request.getReason());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
