package nct.point.controller;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.global.response.PageResponse;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.point.domain.PointExchangeOrder;
import nct.point.dto.AdminExchangeRejectRequest;
import nct.point.dto.AdminPointExchangeAccountResponse;
import nct.point.dto.AdminPointExchangePageResponse;
import nct.point.service.PointExchangeService;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 환전 - 관리자 처리 REST 컨트롤러] (F-PAY-012, D-026)
 *
 * 엔드포인트 (전부 관리자 전용 — /api/admin/**는 SecurityConfig에서 ROLE_ADMIN만 통과):
 *   GET  /api/admin/point/exchange/orders/search   처리 전후 목록 — 상태·검색·페이지 조건
 *   GET  /api/admin/point/exchange/orders/{번호}/account 신청 건 지급 계좌 제한 조회
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
    private final AdminMemberIdentityReader memberIdentityReader;

    /** 담당자 7 · F-PAY-012: 처리 전후 환전 주문을 상태·검색 조건으로 페이지 조회합니다. */
    @GetMapping("/orders/search")
    public ResponseEntity<ApiResponse<AdminPointExchangePageResponse>> getOrders(
            @RequestParam(name = "statusCode", required = false) String statusCode,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<PointExchangeOrder> orders =
                pointExchangeService.getAdminOrderPage(statusCode, keyword, page, size);
        AdminPointExchangePageResponse body = AdminPointExchangePageResponse.from(
                orders,
                identitiesFor(orders.getContent()));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 담당자 7 · F-PAY-012/F-OPS-015: 지급 전 계좌 원문을 감사기록과 함께 제한 조회합니다. */
    @GetMapping("/orders/{ptExcOrdSn}/account")
    public ResponseEntity<ApiResponse<AdminPointExchangeAccountResponse>> getAccount(
            @PathVariable(name = "ptExcOrdSn") long ptExcOrdSn,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        long adminUsrSn = userDetails.getMember().getId();
        AdminPointExchangeAccountResponse body = pointExchangeService.getRequestedAccountForAdmin(
                ptExcOrdSn,
                adminUsrSn,
                httpRequest.getRemoteAddr());
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

    /** 담당자 7 연계: 환전 도메인이 USERS를 직접 조회하지 않도록 회원 읽기 계약만 소비합니다. */
    private Map<Long, AdminMemberIdentityResponse> identitiesFor(List<PointExchangeOrder> orders) {
        Set<Long> userSns = new LinkedHashSet<>();
        for (PointExchangeOrder order : orders) {
            if (order.getUsrSn() != null) userSns.add(order.getUsrSn());
            if (order.getPtExcOrdProcUsrSn() != null) userSns.add(order.getPtExcOrdProcUsrSn());
        }
        return memberIdentityReader.findByUserSns(userSns);
    }
}
