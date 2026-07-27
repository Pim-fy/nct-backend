package nct.ops.security.service;

import java.util.Set;

import nct.ops.risk.service.RiskEventResult;
import nct.ops.security.port.SensitiveDetectionReportResult;

/**
 * 마스킹과 위험 이벤트 기록을 한 번에 수행한 최종 결과다.
 * 채팅·문의 담당 코드는 maskedText를 저장하고, 필요하면 riskEvent로 기록 결과를 확인한다.
 *
 * @param maskedText DB와 화면에 사용해야 하는 마스킹된 문장
 * @param detectedTypes 발견한 개인정보 종류
 * @param riskEvent 개인정보를 발견했을 때 생성·재사용한 위험 이벤트. 미탐지면 null
 * @param report 신고 서비스 연결 결과. 민감정보 미탐지면 null
 */
public record SensitiveDataInspectionResult(
        String maskedText,
        Set<SensitiveDataType> detectedTypes,
        RiskEventResult riskEvent,
        SensitiveDetectionReportResult report) {

    /** 개인정보가 한 종류 이상 발견됐는지 반환한다. */
    public boolean detected() {
        return !detectedTypes.isEmpty();
    }
}
