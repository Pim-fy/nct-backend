package nct.point.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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
import nct.point.domain.SystemSetting;
import nct.point.exception.PointException;
import nct.point.mapper.PointChargeOrderMapper;
import nct.point.mapper.SystemSettingMapper;

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
    private final SystemSettingMapper systemSettingMapper;
    private final TossPaymentsClient tossPaymentsClient;
    private final PointService pointService;
    private final NotificationService notificationService;

    /**
     * 대기(PCOC0001) 주문의 유효시간 — 3시간 (D-025, 2026-07-17 확정).
     * 무통장 입금이 없고 즉시 결제(카드·간편결제)뿐이라 결제는 수 분 안에 끝나거나 버려진다 —
     * 3시간은 결제창을 열어둔 채 자리를 비운 극단적인 경우까지 덮는 버퍼.
     * 별도 배치 없이 승인·조회 시점에 이 기준으로 판정한다 (만료된 대기 건은 이후 아무 일도
     * 일으킬 수 없으므로 상태를 실제로 바꾸는 배치와 실질 효과가 같다).
     */
    private static final Duration PENDING_TTL = Duration.ofHours(3);

    /**
     * 충전 주문 생성 (결제위젯 호출 전). @return 프론트에 넘길 주문번호
     *
     * 최소·최대 충전금액은 SYSTEM_SETTING 값으로 앱 계층에서 검증한다(CHG-003 정본 확정 사항).
     * DB CHECK 제약이 아니라 여기서 막는 이유: 한도가 운영 중 바뀔 수 있는 설정값이라
     * 매번 배포 없이 SYSTEM_SETTING 값만 바꿔서 조정할 수 있어야 하기 때문.
     */
    /**
     * 현재 충전 한도(최소·최대) 조회 — 지갑 충전 모달 안내문용 (2026-07-20).
     * 안내문을 프론트에 하드코딩하면 관리자가 시스템 설정에서 한도를 바꿀 때 안내만 스테일이 되므로,
     * 검증에 실제로 쓰는 값(SYSTEM_SETTING)을 그대로 노출한다 — 안내와 검증의 출처 단일화
     */
    @Transactional(readOnly = true)
    public SystemSetting getChargeLimits() {
        return systemSettingMapper.selectChargeLimits();
    }

    @Transactional
    public String createOrder(long usrSn, long amt) {
        if (amt <= 0) {
            throw new PointException(ErrorCode.POINT_INVALID_AMOUNT, "충전 금액은 0보다 커야 합니다: " + amt);
        }
        SystemSetting limit = systemSettingMapper.selectChargeLimits();
        if (amt < limit.getMinChrgAmt() || amt > limit.getMaxChrgAmt()) {
            throw new PointException(ErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE,
                    String.format("충전 금액은 %,dP ~ %,dP 사이여야 합니다. 현재 입력하신 금액은 %,dP입니다.",
                            limit.getMinChrgAmt(), limit.getMaxChrgAmt(), amt));
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
     *
     * noRollbackFor를 지정하는 이유(2026-07-17 수정): 승인 거절·금액 불일치 시 "실패 상태를 기록하고
     * 예외를 던지는" 흐름인데, PointException은 런타임 예외라 기본 설정에서는 롤백되면서
     * 방금 기록한 실패 상태까지 같이 사라진다(주문이 영영 '대기'로 남음). 실패 기록은 남기고
     * 에러 응답만 내보내도록 PointException은 롤백 대상에서 제외한다.
     * 안전한 이유: 포인트 지급(creditCharge)은 검증(금액·회원 확인)을 전부 쓰기 전에 마치므로,
     * 쓰기가 시작된 뒤에 PointException이 나오는 경로는 의도된 실패 기록 경로뿐이다.
     * (같은 방식 선례: EmailVerificationService의 noRollbackFor)
     */
    @Transactional(noRollbackFor = PointException.class)
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

        // 승인 이후의 내부 반영(지급·완료·알림) — 여기서 실패하면 돈만 나가고 포인트가 없는
        // 최악의 상태가 되므로, 실패 시 보상 절차(D-027)로 넘어간다
        Long ptLdgSn = null;
        try {
            ptLdgSn = pointService.creditCharge(order.getUsrSn(), order.getPtChgOrdAmt(),
                    "포인트 충전 (주문번호 " + orderNo + ")");
            orderMapper.complete(order.getPtChgOrdSn(), PointChargeOrderStatus.COMPLETED.getCode(), paymentKey, ptLdgSn);

            // 같은 트랜잭션 안에서 알림까지 기록 — 충전은 됐는데 알림만 누락되는 일이 없도록
            notificationService.notifyCharge(order.getUsrSn(), order.getPtChgOrdAmt());
        } catch (RuntimeException internalFailure) {
            compensateInternalFailure(order, paymentKey, ptLdgSn, internalFailure);
        }
    }

    /**
     * PG 승인 성공 후 내부 반영 실패 보상 (D-027: ⓐ 즉시 자동 결제취소 + ⓑ 취소 실패 시 관리자 확인).
     *
     * 순서가 중요하다 — 결제취소(외부)를 먼저, DB 기록을 나중에:
     * 내부 반영이 실패한 상황이라 아래 DB 기록마저 실패할 수 있는데, 그 경우에도
     * 돈은 이미 돌려준 상태가 되도록 취소를 먼저 한다. DB 기록이 전부 실패하면
     * 트랜잭션이 통째로 롤백돼 주문은 '대기'로 남고, 3시간 뒤 만료 표시(D-025)로 정리된다.
     *
     * 이 메서드의 DB 기록(회수 원장·실패 상태)은 마지막에 던지는 PointException이
     * noRollbackFor 대상이라 롤백되지 않고 커밋된다.
     */
    private void compensateInternalFailure(PointChargeOrder order, String paymentKey,
                                           Long creditedLdgSn, RuntimeException cause) {
        // ⓐ 자동 결제취소 시도 — 실패해도 예외 없이 false (관리자 확인 경로)
        boolean canceled = tossPaymentsClient.cancel(paymentKey, "충전 내부 처리 실패 자동취소");

        // 지급 원장까지 만들어진 뒤 실패했다면 보정(-) 원장으로 회수 — 합계 0으로 원상복구
        if (creditedLdgSn != null) {
            pointService.reverseCharge(order.getUsrSn(), order.getPtChgOrdAmt(),
                    "충전 내부 처리 실패 회수 (주문번호 " + order.getPtChgOrdNo() + ")");
        }

        orderMapper.fail(order.getPtChgOrdSn(), PointChargeOrderStatus.FAILED.getCode(), paymentKey,
                canceled
                        ? "내부 처리 실패 — 결제 자동취소 완료 (" + cause.getMessage() + ")"
                        : "내부 처리 실패 — 자동취소도 실패, 관리자 확인 필요 (" + cause.getMessage() + ")");

        throw new PointException(ErrorCode.CHARGE_INTERNAL_FAILURE,
                canceled ? "충전 처리 중 오류가 발생하여 결제를 자동취소했습니다. 다시 시도해 주세요."
                         : "충전 처리 중 오류가 발생했습니다. 결제 취소가 지연될 수 있어 관리자가 확인할 예정입니다.");
    }

    /** 내 충전 주문 목록 조회 (최신순 100건, 실패·취소·대기 포함) */
    @Transactional(readOnly = true)
    public List<PointChargeOrder> getOrderList(long usrSn) {
        List<PointChargeOrder> orders = orderMapper.selectListByUser(usrSn);
        orders.forEach(this::markExpiredForDisplay);
        return orders;
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
        // D-025: 3시간 지난 대기 주문은 승인 거부 — 오래 전에 열어둔 결제창으로 뒤늦게 결제되는 것을 막는다
        if (isExpiredPending(order)) {
            throw new PointException(ErrorCode.CHARGE_ORDER_EXPIRED,
                    "시간이 만료된 충전 주문입니다 (대기 3시간 초과): " + orderNo);
        }
        return order;
    }

    /** 대기 상태로 3시간이 지난 주문인지 판정 (D-025) */
    private boolean isExpiredPending(PointChargeOrder o) {
        return PointChargeOrderStatus.PENDING.getCode().equals(o.getPtChgOrdStatusCd())
                && o.getPtChgOrdRegDt() != null
                && o.getPtChgOrdRegDt().plus(PENDING_TTL).isBefore(LocalDateTime.now());
    }

    /**
     * 만료된 대기 건을 이력 화면용으로 "취소(시간 만료)"로 바꿔 보여준다 (D-025).
     * DB의 상태값을 갱신하지 않는 이유: 별도 배치 없이 운영하기로 했고, 만료 판정은
     * 승인 단계(requirePending)에서도 동일하게 적용되므로 이 주문으로는 어떤 결제도
     * 완료될 수 없다 — 화면 표시만 바꿔도 사용자와 시스템이 보는 상태가 어긋나지 않는다.
     */
    private void markExpiredForDisplay(PointChargeOrder o) {
        if (isExpiredPending(o)) {
            o.setPtChgOrdStatusCd(PointChargeOrderStatus.CANCELED.getCode());
            o.setStatusNm("취소");
            o.setPtChgOrdFailRsnCn("시간 만료 (대기 3시간 초과)");
        }
    }
}
