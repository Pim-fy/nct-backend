package nct.ops.security.port;

/**
 * 담당자 5의 ABUSE_REPORT 자동 신고 생성 서비스를 소비하는 연결 규격이다.
 *
 * <p>담당자 5의 구현체가 이 인터페이스를 제공한다. 담당자 7 코드는 ABUSE_REPORT Mapper를
 * 직접 소유하거나 호출하지 않는다.</p>
 */
public interface SensitiveDetectionReportPort {

    SensitiveDetectionReportResult requestReport(SensitiveDetectionReportCommand command);
}
