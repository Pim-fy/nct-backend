package nct.ops.reference.adapter;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.security.service.SensitiveDataMasker;

class AuditCategoryChangeHistoryAdapterTest {

    @Test
    void recordsCategoryChangeToAuditLogWithoutReferenceType() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditCategoryChangeHistoryAdapter adapter = new AuditCategoryChangeHistoryAdapter(
                auditLogService, new SensitiveDataMasker());

        adapter.record(new CategoryChangeHistoryCommand(
                "UPDATE", 7L, 22L, "서비스 카테고리 정렬", "sort=2", "sort=1"));

        verify(auditLogService).record(
                eq(7L),
                eq(AuditLogType.UPDATE),
                isNull(),
                eq(22L),
                contains("categorySn=22"),
                isNull());
    }
}
