package nct.ops.reference.adapter;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.ops.reference.port.BidUnitChangeHistoryCommand;
import nct.ops.reference.port.BidUnitChangeHistoryPort;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * 담당자 7 · F-AUC-013/F-OPS-003/F-OPS-015: 입찰 단위 변경 전후와 사유를 감사로그에 남깁니다.
 * 공통 참조유형에 CMM_CODE가 없으므로 대상 번호와 전후값은 감사 사유에 기록합니다.
 */
@Component
@Primary
@RequiredArgsConstructor
public class AuditBidUnitChangeHistoryAdapter implements BidUnitChangeHistoryPort {

    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_SUMMARY_LENGTH = 120;

    private final AuditLogService auditLogService;
    private final SensitiveDataMasker sensitiveDataMasker;

    @Override
    public void record(BidUnitChangeHistoryCommand command) {
        if (command == null) {
            return;
        }
        auditLogService.record(
                command.actorUserId(),
                type(command.action()),
                null,
                command.bidUnitSn(),
                reason(command),
                null);
    }

    private AuditLogType type(String action) {
        return switch (action == null ? "" : action) {
            case "CREATE" -> AuditLogType.CREATE;
            case "DEACTIVATE" -> AuditLogType.DELETE;
            default -> AuditLogType.UPDATE;
        };
    }

    private String reason(BidUnitChangeHistoryCommand command) {
        String prefix = "입찰 단위 변경 bidUnitSn=" + command.bidUnitSn() + "; reason=";
        String summaries = "; before=" + limit(safe(command.beforeSummary()), MAX_SUMMARY_LENGTH)
                + "; after=" + limit(safe(command.afterSummary()), MAX_SUMMARY_LENGTH);
        int reasonLength = Math.max(0, MAX_REASON_LENGTH - prefix.length() - summaries.length());
        return prefix + limit(safe(command.reason()), reasonLength) + summaries;
    }

    private String safe(String value) {
        return sensitiveDataMasker.maskText(value == null ? "-" : value)
                .replaceAll("[\\r\\n\\t]+", " ");
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
