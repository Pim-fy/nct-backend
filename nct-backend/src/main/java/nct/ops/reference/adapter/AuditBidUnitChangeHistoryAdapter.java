package nct.ops.reference.adapter;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.ops.reference.port.BidUnitChangeHistoryCommand;
import nct.ops.reference.port.BidUnitChangeHistoryPort;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * 담당자 7 · F-AUC-013/F-OPS-003/F-OPS-015: 입찰 단위 변경 전후와 사유를 감사로그에 남깁니다.
 * 공통코드 번호를 주 참조로 사용해 입찰 단위 상세 이력에서 조회할 수 있게 합니다.
 */
@Component
@Primary
@RequiredArgsConstructor
public class AuditBidUnitChangeHistoryAdapter implements BidUnitChangeHistoryPort {

    private static final int MAX_CONTENT_LENGTH = 4000;

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
                RefType.COMMON_CODE,
                command.bidUnitSn(),
                safe(command.reason()),
                safe(command.beforeSummary()),
                safe(command.afterSummary()),
                null,
                null,
                null,
                null);
    }

    private AuditLogType type(String action) {
        return switch (action == null ? "" : action) {
            case "CREATE" -> AuditLogType.CREATE;
            case "DEACTIVATE" -> AuditLogType.DELETE;
            default -> AuditLogType.UPDATE;
        };
    }

    private String safe(String value) {
        String masked = sensitiveDataMasker.maskText(value == null ? "-" : value)
                .replaceAll("[\\r\\n\\t]+", " ");
        return masked.length() <= MAX_CONTENT_LENGTH
                ? masked
                : masked.substring(0, MAX_CONTENT_LENGTH);
    }
}
