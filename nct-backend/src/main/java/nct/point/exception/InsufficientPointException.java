package nct.point.exception;

import nct.global.exception.ErrorCode;

/**
 * [포인트 - 잔액 부족 예외] (ML-PAY-001: 잔액 부족 시 행동 차단)
 * - 서버 측 잔액 재검증에서 사용가능 포인트 < 요청 금액일 때 발생
 * - 프론트 검증과 무관하게 서버에서 반드시 다시 검증한다 (클라이언트 조작 불신 원칙)
 */
public class InsufficientPointException extends PointException {

    private static final long serialVersionUID = 1L;

    public InsufficientPointException(long required, long available) {
        // 동적 메시지: 사용자에게 얼마가 부족한지 구체적으로 안내
        super(ErrorCode.POINT_INSUFFICIENT,
              "사용 가능 포인트가 부족합니다. 필요: " + required + "P, 보유: " + available + "P");
    }
}
