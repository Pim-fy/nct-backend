package nct.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [포인트 - 충전주문상태 코드]
 * - POINT_CHARGE_ORDER.PT_CHG_ORD_STATUS_CD (PCOG01)
 * - 허용 전이: 대기 → 완료 / 대기 → 실패 (승인 거절·금액불일치·통신오류를 모두 실패로 처리)
 */
@Getter
@RequiredArgsConstructor
public enum PointChargeOrderStatus {

    PENDING("PCOC0001"),
    COMPLETED("PCOC0002"),
    FAILED("PCOC0003"),
    CANCELED("PCOC0004");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
