package nct.servicerequest.port;

/** 담당자 7 · 신고 제재: 운영보류 요청서를 이전 상태와 남은 기간으로 복구합니다. */
public record ServiceRequestEnforcementRestoreCommand(
        Long serviceRequestId,
        String previousStatusCode,
        Long remainingSeconds,
        Long adminUserSn) {
}
