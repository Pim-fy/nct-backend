package nct.point.client;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [토스페이먼츠 승인 API 결과]
 * - 성공/실패를 예외가 아닌 값으로 다뤄, 호출부(PointChargeService)가 실패 사유를
 *   주문 테이블에 기록하고 나서 예외를 던지도록 분기하기 쉽게 한다
 */
public record TossConfirmResult(boolean success, long approvedAmount, String failMessage,
                                 String payMethod, String payDetail) {

    public static TossConfirmResult success(long approvedAmount) {
        return success(approvedAmount, null, null);
    }

    /**
     * @param payMethod 결제수단 한글명(토스 응답 method 필드 — "카드"/"간편결제"/"계좌이체" 등)
     * @param payDetail 결제수단 상세(카드는 마스킹된 카드번호, 간편결제는 제공사) — 없으면 null
     */
    public static TossConfirmResult success(long approvedAmount, String payMethod, String payDetail) {
        return new TossConfirmResult(true, approvedAmount, null, payMethod, payDetail);
    }

    public static TossConfirmResult failure(String failMessage) {
        return new TossConfirmResult(false, 0, failMessage, null, null);
    }
}
