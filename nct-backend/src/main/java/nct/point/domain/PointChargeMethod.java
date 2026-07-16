package nct.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nct.global.exception.ErrorCode;
import nct.point.exception.PointException;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [포인트 - 충전 결제 방식]
 * - 토스페이먼츠는 결제창(개별 연동 키 ck/sk)과 결제위젯(전용 키 gck/gsk)이 서로 다른 키 쌍을 쓴다.
 *   두 방식을 나란히 제공하기로 해서(사용자 결정, 2026-07-16) 방식별로 키를 골라야 한다.
 * - 방식은 별도 컬럼 없이 주문번호 접두사에 새긴다 — 승인 시점에 서버가 주문번호만 보고
 *   어느 시크릿 키로 승인할지 판단할 수 있어 프론트가 보낸 값을 신뢰할 필요가 없다.
 */
@Getter
@RequiredArgsConstructor
public enum PointChargeMethod {

    /** 결제창(별창) 방식 — 개별 연동 키(test_ck_/test_sk_) 사용 */
    WINDOW("CHG-"),

    /** 결제위젯 방식 — 위젯 전용 키(test_gck_/test_gsk_) 사용 */
    WIDGET("CHGW-");

    /** 주문번호 접두사 — 주문이 어느 방식으로 생성됐는지 여기서 판별한다 */
    private final String orderNoPrefix;

    /** 프론트 요청 문자열 → enum (null이면 기존 동작 유지 차원에서 결제창) */
    public static PointChargeMethod from(String value) {
        if (value == null || value.isBlank()) {
            return WINDOW;
        }
        try {
            return PointChargeMethod.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PointException(ErrorCode.POINT_INVALID_AMOUNT,
                    "지원하지 않는 충전 방식입니다: " + value);
        }
    }

    /** 주문번호 접두사 → enum. WIDGET 접두사(CHGW-)가 WINDOW 접두사(CHG-)를 포함하므로 긴 쪽 먼저 검사 */
    public static PointChargeMethod fromOrderNo(String orderNo) {
        return orderNo != null && orderNo.startsWith(WIDGET.orderNoPrefix) ? WIDGET : WINDOW;
    }
}
