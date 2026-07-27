package nct.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [포인트 - 포인트분류 코드]
 * - 잔액을 구성하는 세 버킷(PTLG01). 원장 행마다 "어느 버킷의 돈이 움직였는지"를 기록한다
 * - 사용가능: 입찰 등에 쓸 수 있는 포인트 / 홀딩: 입찰로 묶인 포인트 / 정산가능: 판매대금으로 받은 환전 가능 포인트
 * - 잔액 컬럼은 따로 없고, 버킷별 원장 합계(SUM)가 곧 잔액이다 (포인트=현금 원칙: 원장이 유일한 진실)
 */
@Getter
@RequiredArgsConstructor
public enum PointCategory {

    AVAILABLE("PTLC0001"),
    HOLD("PTLC0002"),
    SETTLEABLE("PTLC0003");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
