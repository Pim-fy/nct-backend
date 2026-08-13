package nct.abuse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.dto.MyAbuseReportResponse;
import nct.abuse.mapper.AbuseReportMapper;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

class AbuseReportFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void letsReporterOpenOnlyFileLinkedToOwnReport() throws Exception {
        AbuseReportMapper mapper = mock(AbuseReportMapper.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AbuseReportFileService service = new AbuseReportFileService(
                mapper, fileStorageService, auditLogService);
        Path filePath = Files.createFile(tempDir.resolve("proof.pdf"));
        FileMeta fileMeta = FileMeta.builder().flSn(91L).flOrgNm("proof.pdf").flExt("pdf").build();
        when(mapper.findMyReportById(31L, 7L)).thenReturn(new MyAbuseReportResponse());
        when(mapper.countReportFileLink(31L, 91L)).thenReturn(1);
        when(fileStorageService.requireAbuseReportFile(91L)).thenReturn(fileMeta);
        when(fileStorageService.diskPathOf(fileMeta)).thenReturn(filePath);

        var result = service.getForReporter(7L, 31L, 91L);

        assertThat(result.fileMeta()).isSameAs(fileMeta);
        assertThat(result.diskPath()).isEqualTo(filePath);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void requiresAdminViewReasonBeforeFileLookup() {
        AbuseReportMapper mapper = mock(AbuseReportMapper.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AbuseReportFileService service = new AbuseReportFileService(
                mapper, fileStorageService, auditLogService);

        assertThatThrownBy(() -> service.getForAdmin(7L, 31L, 91L, " ", "127.0.0.1"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD));

        verify(mapper, never()).countReportFileLink(31L, 91L);
    }

    @Test
    void recordsAuditBeforeReturningAdminFile() throws Exception {
        AbuseReportMapper mapper = mock(AbuseReportMapper.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AbuseReportFileService service = new AbuseReportFileService(
                mapper, fileStorageService, auditLogService);
        Path filePath = Files.createFile(tempDir.resolve("evidence.png"));
        FileMeta fileMeta = FileMeta.builder().flSn(91L).flOrgNm("evidence.png").flExt("png").build();
        when(mapper.findReportDetailById(31L)).thenReturn(new AdminAbuseReportResponse());
        when(mapper.countReportFileLink(31L, 91L)).thenReturn(1);
        when(fileStorageService.requireAbuseReportFile(91L)).thenReturn(fileMeta);
        when(fileStorageService.diskPathOf(fileMeta)).thenReturn(filePath);

        service.getForAdmin(7L, 31L, 91L, " 위반 근거 확인 ", "127.0.0.1");

        verify(auditLogService).record(
                eq(7L),
                eq(AuditLogType.SENSITIVE_VIEW),
                eq(RefType.ABUSE_REPORT),
                eq(31L),
                eq("신고 #31 첨부 파일 #91 열람 - 사유: 위반 근거 확인"),
                eq("127.0.0.1"));
    }
}
