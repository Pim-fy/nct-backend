package nct.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 - 환전주문상태 코드] (F-PAY-012, D-026)
 * - POINT_EXCHANGE_ORDER.PT_EXC_ORD_STATUS_CD (PEOG01)
 * - 허용 전이: 신청 → 완료(관리자 수동 이체 후) / 신청 → 반려(차감 포인트 복원)
 *   지급은 관리자 수동이라(D-027과 별개, 환전은 자동화 금지 정본 규칙) 자동 전이가 없다
 */
@Getter
@RequiredArgsConstructor
public enum PointExchangeOrderStatus {

    REQUESTED("PEOC0001"),
    COMPLETED("PEOC0002"),
    REJECTED("PEOC0003");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
