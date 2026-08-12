package nct.ops.audit.adapter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.security.service.SensitiveDataMasker;

class AuditLogServiceAdapterTest {

    @Test
    void mapsStringCommandToAuditLogService() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditLogServiceAdapter adapter = new AuditLogServiceAdapter(auditLogService, new SensitiveDataMasker());

        adapter.record(new AuditLogCommand(
                "ADMIN_APPROVE", "USR:7", "NOTICE", 3L,
                "연락처 010-1234-5678 포함", "before", "after", "req-1"));

        verify(auditLogService).record(
                eq(7L),
                eq(AuditLogType.ADMIN_APPROVE),
                eq(RefType.NOTICE),
                eq(3L),
                eq("연락처 [연락처 마스킹] 포함"),
                eq("before"),
                eq("after"),
                eq("req-1"),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    void rejectsInvalidActorOrActionCode() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditLogServiceAdapter adapter = new AuditLogServiceAdapter(auditLogService, new SensitiveDataMasker());

        assertThrows(IllegalArgumentException.class, () -> adapter.record(new AuditLogCommand(
                "ADMIN_APPROVE", "USR:abc", "NOTICE", 3L, "사유", "-", "-", "req-1")));
        assertThrows(IllegalArgumentException.class, () -> adapter.record(new AuditLogCommand(
                "UNKNOWN_ACTION", "USR:7", "NOTICE", 3L, "사유", "-", "-", "req-1")));
        assertThrows(IllegalArgumentException.class, () -> adapter.record(new AuditLogCommand(
                "ADMIN_APPROVE", "USR:7", "UNKNOWN_REFERENCE", 3L,
                "사유", "-", "-", "req-1")));
    }

    @Test
    void mapsAutomaticSystemActionToNullActor() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditLogServiceAdapter adapter = new AuditLogServiceAdapter(
                auditLogService,
                new SensitiveDataMasker());

        adapter.record(new AuditLogCommand(
                "STATUS_CHANGE", null, "MEMBER", 3L,
                "자동 만료", "before", "after", "req-system-1"));

        verify(auditLogService).record(
                isNull(),
                eq(AuditLogType.STATUS_CHANGE),
                eq(RefType.MEMBER),
                eq(3L),
                eq("자동 만료"),
                eq("before"),
                eq("after"),
                eq("req-system-1"),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    void mapsRelatedReferenceForCrossTargetHistory() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditLogServiceAdapter adapter = new AuditLogServiceAdapter(
                auditLogService,
                new SensitiveDataMasker());

        adapter.record(new AuditLogCommand(
                "STATUS_CHANGE", "7", "MEMBER", 30L,
                "신고 제재", "active", "suspended", "req-2",
                "ABUSE_REPORT", 91L));

        verify(auditLogService).record(
                eq(7L),
                eq(AuditLogType.STATUS_CHANGE),
                eq(RefType.MEMBER),
                eq(30L),
                eq("신고 제재"),
                eq("active"),
                eq("suspended"),
                eq("req-2"),
                eq(RefType.ABUSE_REPORT),
                eq(91L),
                isNull());
    }
}
