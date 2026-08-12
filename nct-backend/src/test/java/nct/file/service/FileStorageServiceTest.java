package nct.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import nct.audit.service.AuditLogService;
import nct.file.domain.FileMeta;
import nct.file.mapper.FileMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

class FileStorageServiceTest {

    @Test
    void rejectsReplacementAfterFileIsLinkedToReport() {
        FileMapper fileMapper = mock(FileMapper.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        FileStorageService service = new FileStorageService(fileMapper, auditLogService);
        FileMeta current = FileMeta.builder()
                .flSn(801L)
                .flPath("/api/attachment/abuse-report/20260811/evidence.png")
                .flRegId("10")
                .build();
        when(fileMapper.findByIdForUpdate(801L)).thenReturn(Optional.of(current));
        when(fileMapper.countAbuseReportFileRefs(801L)).thenReturn(1);
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "changed.png", "image/png", new byte[] { 1 });

        assertThatThrownBy(() -> service.replaceImage(801L, replacement, 10L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_IN_USE));

        verify(fileMapper, never()).updateMeta(any(FileMeta.class));
    }

    @Test
    void doesNotQueryReportLinksWhenReplacingAnotherServiceFile() {
        FileMapper fileMapper = mock(FileMapper.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        FileStorageService service = new FileStorageService(fileMapper, auditLogService);
        FileMeta current = FileMeta.builder()
                .flSn(802L)
                .flPath("/api/attachment/product/20260811/product.png")
                .flRegId("10")
                .build();
        when(fileMapper.findByIdForUpdate(802L)).thenReturn(Optional.of(current));
        MockMultipartFile invalidReplacement = new MockMultipartFile(
                "file", "changed.pdf", "application/pdf", new byte[] { 1 });

        assertThatThrownBy(() -> service.replaceImage(802L, invalidReplacement, 10L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID_TYPE));

        verify(fileMapper, never()).countAbuseReportFileRefs(802L);
        verify(fileMapper, never()).updateMeta(any(FileMeta.class));
    }

    @Test
    void locksReportFileRowBeforeCheckingWhetherItIsAlreadyLinked() {
        FileMapper fileMapper = mock(FileMapper.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        FileStorageService service = new FileStorageService(fileMapper, auditLogService);
        FileMeta current = FileMeta.builder()
                .flSn(803L)
                .flPath("/api/attachment/abuse-report/20260811/evidence.pdf")
                .flRegId("10")
                .build();
        when(fileMapper.findByIdForUpdate(803L)).thenReturn(Optional.of(current));

        assertThat(service.requireOwnedAbuseReportFile(803L, 10L)).isSameAs(current);

        verify(fileMapper).findByIdForUpdate(803L);
        verify(fileMapper).countAbuseReportFileRefs(803L);
    }

    @Test
    void mapperLocksTheFileRowForConcurrentEvidenceChanges() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/mapper/file/FileMapper.xml")) {
            assertThat(input).isNotNull();
            String mapperXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(mapperXml)
                    .contains("id=\"findByIdForUpdate\"")
                    .contains("FOR UPDATE");
        }
    }
}
