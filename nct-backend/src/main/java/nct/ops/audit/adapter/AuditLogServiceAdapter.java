package nct.ops.audit.adapter;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * 담당자 7 · F-OPS-015: 운영 변경 포트를 담당자6의 실제 AUDIT_LOG 저장 서비스에 연결합니다.
 * 다른 도메인은 AUDIT_LOG 테이블을 직접 쓰지 않고 이 포트로 마스킹된 요약만 전달합니다.
 * 운영 감사에서 행위자와 행위 유형은 필수라서 잘못된 값은 조용히 저장하지 않고 실패시킵니다.
 */
@Component
@RequiredArgsConstructor
public class AuditLogServiceAdapter implements AuditLogPort {

    private static final int MAX_REASON_LENGTH = 500;

    private final AuditLogService auditLogService;
    private final SensitiveDataMasker sensitiveDataMasker;

    @Override
    public void record(AuditLogCommand command) {
        if (command == null) {
            return;
        }
        auditLogService.record(
                parseActor(command.actorId()),
                auditType(command.actionCode()),
                refType(command.referenceTypeCode()),
                command.referenceSn(),
                reason(command.reason(), command.beforeSummary(), command.afterSummary(), command.requestId()),
                null);
    }

    private AuditLogType auditType(String actionCode) {
        String normalized = normalize(actionCode);
        return Arrays.stream(AuditLogType.values())
                .filter(type -> type.getCode().equals(normalized) || type.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 감사 행위 코드입니다."));
    }

    private RefType refType(String referenceTypeCode) {
        String normalized = normalize(referenceTypeCode);
        if (normalized == null) {
            return null;
        }
        return Arrays.stream(RefType.values())
                .filter(type -> type.getCode().equals(normalized) || type.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private Long parseActor(String actorId) {
        String normalized = actorId == null ? null : actorId.trim().replaceFirst("(?i)^USR:", "");
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("감사 로그 행위자 정보가 필요합니다.");
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("감사 로그 행위자 형식이 올바르지 않습니다.");
        }
    }

    private String reason(String reason, String beforeSummary, String afterSummary, String requestId) {
        String value = "reason=" + safe(reason)
                + "; before=" + safe(beforeSummary)
                + "; after=" + safe(afterSummary)
                + "; requestId=" + safe(requestId);
        return value.length() <= MAX_REASON_LENGTH ? value : value.substring(0, MAX_REASON_LENGTH);
    }

    private String safe(String value) {
        return sensitiveDataMasker.maskText(value == null ? "-" : value)
                .replaceAll("[\\r\\n\\t]+", " ");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
