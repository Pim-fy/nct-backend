package nct.point.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.point.dto.PointBalanceResponse;
import nct.point.dto.PointChargeConfirmRequest;
import nct.point.dto.PointChargeOrderResponse;
import nct.point.dto.PointChargeRequest;
import nct.point.dto.PointChargeRequestResponse;
import nct.point.dto.PointExchangeOrderResponse;
import nct.point.dto.PointExchangeRequest;
import nct.point.dto.PointLedgerResponse;
import nct.point.service.PointChargeService;
import nct.point.service.PointExchangeService;
import nct.point.service.PointService;

/**
 * [포인트 - REST 컨트롤러] (담당자6)
 *
 * 엔드포인트 (모두 로그인 필요):
 *   GET  /api/point/balance         내 포인트 잔액 (사용가능/홀딩/정산가능/총보유)
 *   GET  /api/point/ledger          내 포인트 원장 목록 (최신순 100건)
 *   POST /api/point/charge/request  충전 주문 생성 (결제창 호출 전) — F-PG-01
 *   POST /api/point/charge/confirm  결제 승인 확정 — F-PG-01
 *   GET  /api/point/charge/orders   내 충전 주문 이력 (실패·취소 포함, 최신순 100건)
 *   POST /api/point/exchange        환전 신청 (즉시 차감 + 계좌 스냅샷) — F-PAY-012, D-026
 *   GET  /api/point/exchange/orders 내 환전 신청 이력 (최신순 100건)
 *
 * 설계 원칙:
 * - 사용자 식별은 항상 인증 토큰(@AuthenticationPrincipal)에서 꺼낸다.
 *   usrSn을 요청 파라미터로 받으면 남의 지갑을 조회할 수 있으므로 절대 금지.
 * - 홀딩/반환/보관금전환 같은 명령 계약은 HTTP로 노출하지 않는다 —
 *   입찰·경매·거래 도메인 서비스가 서버 내부에서만 호출한다 (PointService 참조).
 * - 환전 지급완료/반려(관리자 처리) API는 후속 범위 — 지급·승인 자동화 금지 정본 규칙에 따라
 *   관리자 수동 처리 흐름이 정해진 뒤 추가한다.
 */
@RestController
@RequestMapping("/api/point")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;
    private final PointChargeService pointChargeService;
    private final PointExchangeService pointExchangeService;

    /** 결제위젯 방식 클라이언트 키 (gck) — 프로젝트 루트 .env 파일에서 읽는다 */
    @Value("${toss.payments.widget.client-key}")
    private String widgetClientKey;

    /** 내 포인트 잔액 조회 */
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getBalance(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId(); // 토큰의 본인 식별자만 사용
        PointBalanceResponse body = PointBalanceResponse.from(pointService.getBalance(usrSn));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 내 포인트 원장 목록 조회 (최신순 100건) */
    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<List<PointLedgerResponse>>> getLedger(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        List<PointLedgerResponse> body = pointService.getLedgerList(usrSn).stream()
                .map(PointLedgerResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 충전 주문 생성 — 결제위젯을 띄우기 전에 서버가 먼저 신뢰 기준 금액을 기록한다 */
    @PostMapping("/charge/request")
    public ResponseEntity<ApiResponse<PointChargeRequestResponse>> requestCharge(
            @Valid @RequestBody PointChargeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        String orderId = pointChargeService.createOrder(usrSn, request.getAmount());
        return ResponseEntity.ok(ApiResponse.success(
                PointChargeRequestResponse.of(orderId, request.getAmount(), widgetClientKey)));
    }

    /** 결제 승인 확정 — Toss confirm API 응답 금액이 주문 생성 시 기록과 일치할 때만 포인트를 지급한다 */
    @PostMapping("/charge/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmCharge(
            @Valid @RequestBody PointChargeConfirmRequest request) {

        pointChargeService.confirm(request.getOrderId(), request.getPaymentKey());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 내 충전 주문 이력 조회 — 확정 건(원장)과 달리 실패·취소·대기 시도까지 전부 보여준다 */
    @GetMapping("/charge/orders")
    public ResponseEntity<ApiResponse<List<PointChargeOrderResponse>>> getChargeOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        List<PointChargeOrderResponse> body = pointChargeService.getOrderList(usrSn).stream()
                .map(PointChargeOrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 환전 신청 — 즉시 차감 + 신청 시점 계좌 스냅샷, "며칠 내 지급 예정" 알림 (F-PAY-012) */
    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<Void>> requestExchange(
            @Valid @RequestBody PointExchangeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        pointExchangeService.apply(usrSn, request.getAmount());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 내 환전 신청 이력 조회 (신청·완료·반려, 최신순 100건) */
    @GetMapping("/exchange/orders")
    public ResponseEntity<ApiResponse<List<PointExchangeOrderResponse>>> getExchangeOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        List<PointExchangeOrderResponse> body = pointExchangeService.getOrderList(usrSn).stream()
                .map(PointExchangeOrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
