package nct.point.client;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [토스페이먼츠 주문번호 조회 API 결과] (2단계 대사 배치 전용)
 *
 * confirm 콜백을 못 받은 결제(사용자가 결제 성공 직후 브라우저를 닫는 등)를 서버가 사후에
 * 직접 확인할 때 쓴다. 세 가지 결과를 명확히 구분한다 — 셋을 뭉뚱그리면 배치가 "확인 못 함"과
 * "결제 자체가 없었음"을 혼동해 통신 장애 중에 멀쩡한 주문을 실패 처리해버릴 수 있다:
 * - unreachable(): 토스 통신 실패(네트워크 오류·인증 오류 등) — 이번엔 확인 못 함, 다음 주기에 재시도
 * - notFound(): 토스에 그 주문번호로 결제 시도 이력 자체가 없음(404) — 결제창을 그냥 닫고 나간 경우
 * - found(...): 결제 이력이 있음 — status로 실제 결제완료(DONE)인지 취소/만료/진행중인지 판단
 */
public record TossOrderLookupResult(boolean reachable, boolean found, String status,
                                     long totalAmount, String paymentKey) {

    public static TossOrderLookupResult unreachable() {
        return new TossOrderLookupResult(false, false, null, 0, null);
    }

    public static TossOrderLookupResult notFound() {
        return new TossOrderLookupResult(true, false, null, 0, null);
    }

    public static TossOrderLookupResult found(String status, long totalAmount, String paymentKey) {
        return new TossOrderLookupResult(true, true, status, totalAmount, paymentKey);
    }

    /** 토스 쪽에서 실제로 결제가 완료된 상태인지 */
    public boolean isDone() {
        return found && "DONE".equals(status);
    }
}
