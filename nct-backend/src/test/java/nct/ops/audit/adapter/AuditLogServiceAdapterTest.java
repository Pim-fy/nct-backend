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
                eq("reason=연락처 [연락처 마스킹] 포함; before=before; after=after; requestId=req-1"),
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
    }
}
