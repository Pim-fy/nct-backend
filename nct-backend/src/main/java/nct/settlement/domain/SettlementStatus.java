package nct.settlement.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [정산 - 정산상태 코드]
 * - 정산상태 공통코드(STLG01). 허용 전이: 대기 → 완료 / 대기 ↔ 보류 (그 외 전이는 예외)
 * - 상태 머신을 지키는 이유: 보류(분쟁) 중인 정산이 실수로 완료 처리되어
 *   돈이 빠져나가는 사고를 상태 검증 한 곳에서 차단하기 위함
 */
@Getter
@RequiredArgsConstructor
public enum SettlementStatus {

    PENDING("STLC0001"),
    ON_HOLD("STLC0002"),
    COMPLETED("STLC0003");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
