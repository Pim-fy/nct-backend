package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.port.AdminTradeDisputeReader;

/** 담당자 7 · F-OPS-005: 분쟁 증빙의 연결 검증과 감사로그 선행 기록을 검증합니다. */
class AdminDisputeEvidenceFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsLinkedFileAndRecordsSensitiveView() throws Exception {
        AdminTradeDisputeReader disputeReader = mock(AdminTradeDisputeReader.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AdminDisputeEvidenceFileService service = new AdminDisputeEvidenceFileService(
                disputeReader,
                fileStorageService,
                auditLogService);
        Path diskFile = Files.writeString(tempDir.resolve("proof.pdf"), "proof");
        FileMeta fileMeta = FileMeta.builder().flSn(91L).flOrgNm("proof.pdf").flExt("pdf").build();
        when(disputeReader.hasEvidenceFile(11L, 91L)).thenReturn(true);
        when(fileStorageService.requireTradeDisputeFile(91L)).thenReturn(fileMeta);
        when(fileStorageService.diskPathOf(fileMeta)).thenReturn(diskFile);

        var result = service.getForAdmin(7L, 11L, 91L, "분쟁 판정 자료 확인", "127.0.0.1");

        assertThat(result.fileMeta()).isSameAs(fileMeta);
        assertThat(result.diskPath()).isEqualTo(diskFile);
        verify(auditLogService).record(
                7L,
                AuditLogType.SENSITIVE_VIEW,
                RefType.TRADE_DISPUTE,
                11L,
                "거래 분쟁 증빙 열람 - 파일 #91 - 사유: 분쟁 판정 자료 확인",
                "127.0.0.1");
    }

    @Test
    void hidesUnlinkedFileAsNotFoundWithoutAudit() {
        AdminTradeDisputeReader disputeReader = mock(AdminTradeDisputeReader.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AdminDisputeEvidenceFileService service = new AdminDisputeEvidenceFileService(
                disputeReader,
                fileStorageService,
                auditLogService);

        assertThatThrownBy(() -> service.getForAdmin(
                7L,
                11L,
                91L,
                "분쟁 판정 자료 확인",
                "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);

        verify(fileStorageService, never()).requireTradeDisputeFile(91L);
        verify(auditLogService, never()).record(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsBlankReasonBeforeFileLookup() {
        AdminTradeDisputeReader disputeReader = mock(AdminTradeDisputeReader.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AdminDisputeEvidenceFileService service = new AdminDisputeEvidenceFileService(
                disputeReader,
                fileStorageService,
                auditLogService);

        assertThatThrownBy(() -> service.getForAdmin(7L, 11L, 91L, "  ", "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);

        verify(disputeReader, never()).hasEvidenceFile(11L, 91L);
        verify(auditLogService, never()).record(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsReasonLongerThanContractLimit() {
        AdminTradeDisputeReader disputeReader = mock(AdminTradeDisputeReader.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AdminDisputeEvidenceFileService service = new AdminDisputeEvidenceFileService(
                disputeReader,
                fileStorageService,
                auditLogService);

        assertThatThrownBy(() -> service.getForAdmin(
                7L,
                11L,
                91L,
                "a".repeat(1001),
                "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(disputeReader, never()).hasEvidenceFile(11L, 91L);
    }
}
