package nct.ops.risk.service;

/**
 * 위험 이벤트 생성을 요청할 때 전달하는 값 묶음이다.
 *
 * @param typeCode 위험 종류 코드(RSKG01 소속)
 * @param referenceTypeCode 관련 데이터 종류(REFG01 소속). 참조가 없으면 null
 * @param referenceSn 관련 데이터 고유번호. referenceTypeCode가 null이면 함께 null
 * @param content 개인정보 원문이 아닌 운영용 설명
 * @param actorId 요청한 사용자 또는 시스템 식별자. 없으면 SYSTEM으로 기록
 */
public record RiskEventCommand(
        String typeCode,
        String referenceTypeCode,
        Long referenceSn,
        String content,
        String actorId) {
}
