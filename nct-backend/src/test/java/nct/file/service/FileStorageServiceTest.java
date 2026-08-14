package nct.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import nct.audit.service.AuditLogService;
import nct.file.domain.FileMeta;
import nct.file.mapper.FileMapper;

class FileStorageServiceTest {

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
