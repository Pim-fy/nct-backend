package nct.ops.security.port;

/**
 * 담당자 5의 ABUSE_REPORT 생성 서비스가 맞춰 주어야 하는 교체형 연결 규격이다.
 *
 * <p>현재 담당자 5 코드가 아직 없으므로 임시 어댑터가 연결된다. 나중에는 담당자 5가
 * 이 인터페이스 구현체를 제공하면 되고, 담당자 7 코드는 ABUSE_REPORT Mapper를
 * 직접 소유하거나 호출하지 않는다.</p>
 */
public interface SensitiveDetectionReportPort {

    SensitiveDetectionReportResult requestReport(SensitiveDetectionReportCommand command);
}
