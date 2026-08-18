package nct.audit.adapter;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.audit.mapper.AuditLogMapper;
import nct.audit.service.AuditLogService;
import nct.ops.risk.port.AdminLoginFailureSignalStore;

/** 담당자 7 연계 · REQ-OPS-011: 실패 감사기록을 리스크 후속 처리와 분리해 보존합니다. */
@Component
@RequiredArgsConstructor
public class AdminLoginFailureSignalAdapter implements AdminLoginFailureSignalStore {

    private final AuditLogService auditLogService;
    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String identityToken, String ipToken) {
        auditLogService.record(
                null,
                AuditLogType.LOGIN_FAILURE,
                null,
                null,
                "관리자 로그인 실패",
                null,
                "identityToken=" + identityToken + ";ipToken=" + ipToken,
                null,
                null,
                null,
                null);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSince(String tokenField, String tokenValue, LocalDateTime since) {
        return auditLogMapper.countAdminLoginFailuresByTokenSince(
                tokenField, tokenValue, since);
    }
}
