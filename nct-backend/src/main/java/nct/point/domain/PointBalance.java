package nct.point.domain;

import lombok.Data;

/**
 * [포인트 - 잔액 요약 모델]
 * - POINT_LEDGER의 포인트분류(PTLG01)별 합계(SUM)로 계산되는 파생 값
 * - DB에 잔액 컬럼을 두지 않는 이유: 컬럼 UPDATE 방식은 동시 요청·버그로 원장과 어긋날 수 있지만,
 *   원장 합계는 기록이 곧 잔액이라 어긋날 수가 없다 (포인트_모듈_가이드라인 §4)
 */
@Data
public class PointBalance {

    /** 사용가능 포인트 (PTLC0001 합계) */
    private long availableAmt;

    /** 홀딩 포인트 (PTLC0002 합계) — 입찰 등으로 묶여 있는 금액 */
    private long holdAmt;

    /** 정산가능(환전가능) 포인트 (PTLC0003 합계) — 판매대금으로 받은 금액 */
    private long settleableAmt;

    /** 총 보유 포인트 = 세 버킷의 합 */
    public long getTotalAmt() {
        return availableAmt + holdAmt + settleableAmt;
    }
}
