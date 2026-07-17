package nct.ops.security.adapter;

import nct.ops.security.port.SensitiveDetectionReportCommand;
import nct.ops.security.port.SensitiveDetectionReportPort;
import nct.ops.security.port.SensitiveDetectionReportResult;

/**
 * 담당자 5의 실제 신고 서비스가 들어오기 전까지 서버 실행을 가능하게 하는 임시 플러그다.
 *
 * <p>ABUSE_REPORT에는 아무것도 쓰지 않으며 결과를 DEFERRED로 명확히 돌려준다.
 * 실제 {@link SensitiveDetectionReportPort} Bean이 추가되면 별도 설정이 이 임시 객체를
 * 만들지 않으므로, 이 파일을 호출부마다 찾아 바꾸지 않아도 된다.</p>
 */
public class DeferredSensitiveDetectionReportAdapter implements SensitiveDetectionReportPort {

    @Override
    public SensitiveDetectionReportResult requestReport(SensitiveDetectionReportCommand command) {
        return SensitiveDetectionReportResult.deferred();
    }
}
