package nct.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [포인트 - 원장유형 코드]
 * - 원장 행이 발생한 이벤트 종류(PTLG02). "왜 돈이 움직였는지"를 기록한다
 * - 흐름: 충전 → (입찰)홀딩 → 반환(상위입찰) 또는 보관금전환(낙찰) → 정산(거래완료) / 보정(관리자 수동)
 */
@Getter
@RequiredArgsConstructor
public enum PointLedgerType {

    CHARGE("PTLC0004"),
    HOLD("PTLC0005"),
    RELEASE("PTLC0006"),
    ESCROW("PTLC0007"),
    SETTLE("PTLC0008"),
    ADJUST("PTLC0009"),
    /** 환전 신청 즉시 차감 (F-PAY-012, D-026 — 2026-07-17 공통코드 추가) */
    EXCHANGE_OUT("PTLC0010"),
    /** 환전 반려 시 복원 */
    EXCHANGE_RESTORE("PTLC0011"),
    /** 정산가능→사용가능 전환 (F-PAY-010 — 2026-07-18 공통코드 추가). 전환 1건 = −/+ 두 행 모두 이 유형 */
    CONVERT("PTLC0012"),
    /**
     * 분쟁 판정 보관금 환불 (사용자 결정 2026-07-20 — 공통코드 PTLC0013 신설, 정본 반영 요청 별도).
     * 보관금전환(ESCROW −)으로 빠졌던 거래대금을 구매자/의뢰자의 사용가능 버킷에 +행으로 되돌린다 —
     * 같은 참조의 ESCROW와 합산하면 0이 되어 "보관금 소멸"이 원장만으로 판정된다
     */
    REFUND("PTLC0013");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
