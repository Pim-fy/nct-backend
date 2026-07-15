package nct.point.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.point.client.TossConfirmResult;
import nct.point.client.TossPaymentsClient;
import nct.point.domain.PointChargeOrder;
import nct.point.domain.PointChargeOrderStatus;
import nct.point.exception.PointException;
import nct.point.mapper.PointChargeOrderMapper;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [포인트 충전 - 서비스 계약] (담당자6 백종남, F-PG-01)
 *
 * 결제창을 띄우기 전에 서버가 먼저 주문(금액 신뢰 기준)을 기록해 두고,
 * 승인 단계에서 PG 응답 금액을 이 기록과 대조한 뒤에만 포인트를 지급한다(QSC-PG-01).
 * 최종 승인 판단은 이 서버의 confirm 호출 결과만 신뢰한다(QSC-PG-03) — 프론트의 성공 콜백은 신뢰하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PointChargeService {

    private final PointChargeOrderMapper orderMapper;
    private final TossPaymentsClient tossPaymentsClient;
    private final PointService pointService;
    private final NotificationService notificationService;

    /** 충전 주문 생성 (결제창 호출 전). @return 프론트에 넘길 주문번호 */
    @Transactional
    public String createOrder(long usrSn, long amt) {
        if (amt <= 0) {
            throw new PointException(ErrorCode.POINT_INVALID_AMOUNT, "충전 금액은 0보다 커야 합니다: " + amt);
        }
        String orderNo = "CHG-" + usrSn + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        PointChargeOrder order = new PointChargeOrder();
        order.setUsrSn(usrSn);
        order.setPtChgOrdNo(orderNo);
        order.setPtChgOrdAmt(amt);
        order.setPtChgOrdStatusCd(PointChargeOrderStatus.PENDING.getCode());
        orderMapper.insert(order);
        return orderNo;
    }

    /**
     * 결제 승인 확정. 프론트가 결제창 성공 콜백을 받은 뒤 호출한다.
     * Toss confirm API에는 항상 주문 생성 시 기록해 둔 금액만 넘기고, 그 응답 금액이
     * 사전 기록과 정확히 일치할 때만 포인트를 지급한다 — 이 두 금액 중 어느 쪽도 프론트가 준 값이 아니다.
     */
    @Transactional
    public void confirm(String orderNo, String paymentKey) {
        PointChargeOrder order = requirePending(orderNo);

        TossConfirmResult result = tossPaymentsClient.confirm(paymentKey, orderNo, order.getPtChgOrdAmt());

        if (!result.success()) {
            orderMapper.fail(order.getPtChgOrdSn(), PointChargeOrderStatus.FAILED.getCode(),
                    paymentKey, result.failMessage());
            throw new PointException(ErrorCode.EXTERNAL_API_ERROR, "결제 승인 실패: " + result.failMessage());
        }

        // 위변조 방지 핵심: Toss가 승인한 실제 금액과 사전 기록 금액이 정확히 일치할 때만 반영
        if (result.approvedAmount() != order.getPtChgOrdAmt()) {
            orderMapper.fail(order.getPtChgOrdSn(), PointChargeOrderStatus.FAILED.getCode(), paymentKey,
                    "승인 금액 불일치 (기록: " + order.getPtChgOrdAmt() + ", 승인: " + result.approvedAmount() + ")");
            throw new PointException(ErrorCode.CHARGE_AMOUNT_MISMATCH,
                    "결제 승인 금액이 사전 기록과 일치하지 않습니다.");
        }

        long ptLdgSn = pointService.creditCharge(order.getUsrSn(), order.getPtChgOrdAmt(),
                "포인트 충전 (주문번호 " + orderNo + ")");
        orderMapper.complete(order.getPtChgOrdSn(), PointChargeOrderStatus.COMPLETED.getCode(), paymentKey, ptLdgSn);

        // 같은 트랜잭션 안에서 알림까지 기록 — 충전은 됐는데 알림만 누락되는 일이 없도록
        notificationService.notifyCharge(order.getUsrSn(), order.getPtChgOrdAmt());
    }

    /** 상태 전이 사전 검증 — 행 잠금 후 대기 상태인지 확인 */
    private PointChargeOrder requirePending(String orderNo) {
        PointChargeOrder order = orderMapper.selectForUpdateByOrderNo(orderNo);
        if (order == null) {
            throw new PointException(ErrorCode.CHARGE_ORDER_NOT_FOUND, "존재하지 않는 충전 주문입니다: " + orderNo);
        }
        if (!PointChargeOrderStatus.PENDING.getCode().equals(order.getPtChgOrdStatusCd())) {
            throw new PointException(ErrorCode.CHARGE_ORDER_ALREADY_PROCESSED,
                    "이미 처리된 충전 주문입니다: " + orderNo);
        }
        return order;
    }
}
