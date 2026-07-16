package nct.ops.security.port;

/**
 * 신고 기능 연결 결과다. 실제 신고 번호는 담당자 5 구현이 연결된 뒤에만 채워진다.
 */
public record SensitiveDetectionReportResult(Status status, Long reportSn) {

    public enum Status {
        /** 담당자 5 코드가 아직 연결되지 않아 RISK_EVENT까지만 안전하게 기록된 상태다. */
        DEFERRED,
        /** 담당자 5 서비스가 새 신고를 만든 상태다. */
        CREATED,
        /** 이미 연결된 신고가 있어 기존 결과를 재사용한 상태다. */
        REUSED
    }

    public static SensitiveDetectionReportResult deferred() {
        return new SensitiveDetectionReportResult(Status.DEFERRED, null);
    }
}
