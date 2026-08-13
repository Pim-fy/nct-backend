package nct.ops.reference.adapter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.security.service.SensitiveDataMasker;

class AuditCategoryChangeHistoryAdapterTest {

    @Test
    void recordsCategoryChangeWithStructuredReferenceAndSummaries() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditCategoryChangeHistoryAdapter adapter = new AuditCategoryChangeHistoryAdapter(
                auditLogService, new SensitiveDataMasker());

        adapter.record(new CategoryChangeHistoryCommand(
                "UPDATE", 7L, 22L, "서비스 카테고리 정렬", "sort=2", "sort=1"));

        verify(auditLogService).record(
                eq(7L),
                eq(AuditLogType.UPDATE),
                eq(RefType.CATEGORY),
                eq(22L),
                eq("서비스 카테고리 정렬"),
                eq("sort=2"),
                eq("sort=1"),
                isNull(),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    void keepsBeforeAndAfterSummariesWithLongMemo() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditCategoryChangeHistoryAdapter adapter = new AuditCategoryChangeHistoryAdapter(
                auditLogService, new SensitiveDataMasker());

        adapter.record(new CategoryChangeHistoryCommand(
                "UPDATE",
                7L,
                22L,
                "긴 메모".repeat(200),
                "name=변경 전 카테고리,sort=20,use=Y",
                "name=변경 후 카테고리,sort=30,use=N"));

        verify(auditLogService).record(
                eq(7L),
                eq(AuditLogType.UPDATE),
                eq(RefType.CATEGORY),
                eq(22L),
                eq("긴 메모".repeat(200)),
                eq("name=변경 전 카테고리,sort=20,use=Y"),
                eq("name=변경 후 카테고리,sort=30,use=N"),
                isNull(),
                isNull(),
                isNull(),
                isNull());
    }
}
