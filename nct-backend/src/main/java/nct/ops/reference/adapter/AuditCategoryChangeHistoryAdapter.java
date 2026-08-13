package nct.ops.reference.adapter;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.reference.port.CategoryChangeHistoryPort;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * 담당자 7 · F-COM-003/F-OPS-015: 카테고리 변경 이력을 실제 AUDIT_LOG에 남깁니다.
 * 카테고리 번호를 주 참조로 사용해 카테고리 상세의 공통 히스토리에서 조회할 수 있게 합니다.
 */
@Component
@Primary
@RequiredArgsConstructor
public class AuditCategoryChangeHistoryAdapter implements CategoryChangeHistoryPort {

    private static final int MAX_CONTENT_LENGTH = 4000;

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
                RefType.CATEGORY,
                command.categorySn(),
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
            case "DELETE" -> AuditLogType.DELETE;
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
