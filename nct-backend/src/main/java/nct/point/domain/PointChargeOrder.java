package nct.point.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [포인트 - 충전 주문 모델]
 * - POINT_CHARGE_ORDER 한 행. 결제창을 띄우기 전에 서버가 먼저 기록해 두는 신뢰 기준 금액
 * - PG 승인 응답 금액을 이 기록과 대조해야만 포인트를 지급한다 (QSC-PG-01, 금액 위변조 검증)
 */
@Data
public class PointChargeOrder {

    private Long ptChgOrdSn;
    private Long usrSn;

    /** 주문번호 (Toss orderId, 서버가 생성) */
    private String ptChgOrdNo;

    /** 주문금액 — 서버가 사전 기록한 신뢰 기준 금액. 프론트가 이후 뭐라 보내든 이 값만 신뢰한다 */
    private long ptChgOrdAmt;

    /** 충전주문상태공통코드(PCOG01) */
    private String ptChgOrdStatusCd;

    /** PG결제키(paymentKey) — 승인 성공 후 저장 */
    private String ptChgOrdPgKey;

    /** 완료 후 생성된 포인트원장일련번호 */
    private Long ptLdgSn;

    private String ptChgOrdFailRsnCn;

    private LocalDateTime ptChgOrdRegDt;
    private LocalDateTime ptChgOrdUpdtDt;
}
