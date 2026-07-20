package nct.ops.notice.adapter;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.ops.notice.port.NoticeChangeHistoryCommand;
import nct.ops.notice.port.NoticeChangeHistoryPort;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * 담당자 7 · F-COM-013/F-OPS-015: 공지 등록·수정·숨김·삭제 이력을 실제 AUDIT_LOG에 남깁니다.
 * 공지 본문 원문은 저장하지 않고, 화면 추적에 필요한 짧은 전후 요약과 사유만 마스킹해 기록합니다.
 */
@Component
@Primary
@RequiredArgsConstructor
public class AuditNoticeChangeHistoryAdapter implements NoticeChangeHistoryPort {

    private static final int MAX_REASON_LENGTH = 500;

    private final AuditLogService auditLogService;
    private final SensitiveDataMasker sensitiveDataMasker;

    @Override
    public void record(NoticeChangeHistoryCommand command) {
        if (command == null) {
            return;
        }
        auditLogService.record(
                command.getActorUserId(),
                type(command.getAction()),
                RefType.NOTICE,
                command.getNoticeId(),
                reason(command.getReason(), command.getBeforeSummary(), command.getAfterSummary()),
                null);
    }

    private AuditLogType type(String action) {
        return switch (action == null ? "" : action) {
            case "CREATE" -> AuditLogType.CREATE;
            case "DELETE" -> AuditLogType.DELETE;
            case "HIDE" -> AuditLogType.STATUS_CHANGE;
            default -> AuditLogType.UPDATE;
        };
    }

    private String reason(String reason, String beforeSummary, String afterSummary) {
        String value = "공지 변경 사유=" + safe(reason)
                + "; before=" + safe(beforeSummary)
                + "; after=" + safe(afterSummary);
        return value.length() <= MAX_REASON_LENGTH ? value : value.substring(0, MAX_REASON_LENGTH);
    }

    private String safe(String value) {
        return sensitiveDataMasker.maskText(value == null ? "-" : value)
                .replaceAll("[\\r\\n\\t]+", " ");
    }
}
