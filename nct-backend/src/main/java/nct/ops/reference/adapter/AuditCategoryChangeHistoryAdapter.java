package nct.ops.reference.adapter;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.reference.port.CategoryChangeHistoryPort;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * 담당자 7 · F-COM-003/F-OPS-015: 카테고리 변경 이력을 실제 AUDIT_LOG에 남깁니다.
 * 현재 공통 RefType에 CATEGORY가 없어 참조유형은 비우고, 자동 작업명과 전후값에 categorySn을 명시합니다.
 */
@Component
@Primary
@RequiredArgsConstructor
public class AuditCategoryChangeHistoryAdapter implements CategoryChangeHistoryPort {

    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_SUMMARY_LENGTH = 160;

    private final AuditLogService auditLogService;
    private final SensitiveDataMasker sensitiveDataMasker;

    @Override
    public void record(CategoryChangeHistoryCommand command) {
        if (command == null) {
            return;
        }
        auditLogService.record(
                command.actorUserId(),
                type(command.action()),
                null,
                command.categorySn(),
                reason(command),
                null);
    }

    private AuditLogType type(String action) {
        return switch (action == null ? "" : action) {
            case "CREATE" -> AuditLogType.CREATE;
            case "DELETE" -> AuditLogType.DELETE;
            default -> AuditLogType.UPDATE;
        };
    }

    private String reason(CategoryChangeHistoryCommand command) {
        String prefix = "카테고리 변경 categorySn=" + command.categorySn() + "; reason=";
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
