package nct.ops.risk.service;

/**
 * 위험 이벤트 기록 결과다.
 *
 * @param riskEventSn 새로 만들었거나 기존에 존재하던 이벤트 고유번호
 * @param created 이번 요청에서 새 행을 만들었으면 true, 중복이라 기존 행을 썼으면 false
 */
public record RiskEventResult(Long riskEventSn, boolean created) {
}
