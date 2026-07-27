package nct.ops.security.port;

import java.util.Set;

import nct.ops.security.service.SensitiveDataType;

/**
 * F-OPS-013 탐지 결과를 담당자 5의 신고 기능에 넘길 때 사용하는 안전한 값 묶음이다.
 *
 * <p>사용자가 입력한 원문과 마스킹 전 연락처는 포함하지 않는다. 담당자 5는
 * {@code riskEventSn}을 멱등성 기준으로 사용해 같은 위험 이벤트에 신고가 두 번
 * 만들어지지 않도록 구현해야 한다.</p>
 */
public record SensitiveDetectionReportCommand(
        Long riskEventSn,
        String referenceTypeCode,
        Long referenceSn,
        Set<SensitiveDataType> detectedTypes,
        String actorId) {
}
