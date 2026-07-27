package nct.point.client;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [토스페이먼츠 승인 API 결과]
 * - 성공/실패를 예외가 아닌 값으로 다뤄, 호출부(PointChargeService)가 실패 사유를
 *   주문 테이블에 기록하고 나서 예외를 던지도록 분기하기 쉽게 한다
 */
public record TossConfirmResult(boolean success, long approvedAmount, String failMessage) {

    public static TossConfirmResult success(long approvedAmount) {
        return new TossConfirmResult(true, approvedAmount, null);
    }

    public static TossConfirmResult failure(String failMessage) {
        return new TossConfirmResult(false, 0, failMessage);
    }
}
