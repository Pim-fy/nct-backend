package nct.ops.security.port;

/**
 * 위험 이벤트를 기준으로 생성하거나 재사용한 자동 신고의 결과다.
 */
public record SensitiveDetectionReportResult(Status status, Long reportSn) {

    public enum Status {
        /** 담당자 5 서비스가 새 신고를 만든 상태다. */
        CREATED,
        /** 이미 연결된 신고가 있어 기존 결과를 재사용한 상태다. */
        REUSED
    }
}
