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
 * 공지 본문 원문은 저장하지 않고, 자동 작업명(삭제는 입력 사유)과 짧은 전후 요약만 마스킹해 기록합니다.
 */
@Component
@Primary
@RequiredArgsConstructor
public class AuditNoticeChangeHistoryAdapter implements NoticeChangeHistoryPort {

    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_SUMMARY_LENGTH = 160;

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
            case "PUBLISH", "HIDE" -> AuditLogType.STATUS_CHANGE;
            default -> AuditLogType.UPDATE;
        };
    }

    private String reason(String reason, String beforeSummary, String afterSummary) {
        String prefix = "공지 변경 사유=";
        String summaries = "; before=" + limit(safe(beforeSummary), MAX_SUMMARY_LENGTH)
                + "; after=" + limit(safe(afterSummary), MAX_SUMMARY_LENGTH);
        int reasonLength = Math.max(0, MAX_REASON_LENGTH - prefix.length() - summaries.length());
        return prefix + limit(safe(reason), reasonLength) + summaries;
    }

    private String safe(String value) {
        return sensitiveDataMasker.maskText(value == null ? "-" : value)
                .replaceAll("[\\r\\n\\t]+", " ");
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
