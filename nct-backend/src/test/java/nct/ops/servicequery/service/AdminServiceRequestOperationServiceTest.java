package nct.ops.servicequery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.common.domain.RefType;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.quote.port.AdminQuoteModerationPort;
import nct.quote.port.AdminQuoteModerationPort.AdminQuoteModerationResult;
import nct.servicerequest.port.AdminServiceRequestCommandPort;

/** 담당자 7 · F-OPS-015/021: 견적 조치가 상위 서비스 요청 이력에도 연결되는지 검증합니다. */
@ExtendWith(MockitoExtension.class)
class AdminServiceRequestOperationServiceTest {

    @Mock private AdminServiceRequestCommandPort serviceRequestCommandPort;
    @Mock private AdminQuoteModerationPort quoteModerationPort;
    @Mock private AuditLogPort auditLogPort;
    @Mock private NotificationService notificationService;

    private AdminServiceRequestOperationService service;

    @BeforeEach
    void setUp() {
        service = new AdminServiceRequestOperationService(
                serviceRequestCommandPort,
                quoteModerationPort,
                auditLogPort,
                notificationService);
    }

    @Test
    void invalidatedQuoteLinksBackToServiceRequestHistory() {
        when(quoteModerationPort.invalidateActiveQuote(41L, 51L, 7L))
                .thenReturn(new AdminQuoteModerationResult(
                        41L, 51L, 101L, "QUTC0001", "QUTC0005", true));

        service.invalidateQuote(41L, 51L, "운영 정책 위반", "request-1", 7L);

        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().referenceTypeCode())
                .isEqualTo(RefType.QUOTE.getCode());
        assertThat(auditCaptor.getValue().referenceSn()).isEqualTo(51L);
        assertThat(auditCaptor.getValue().relatedReferenceTypeCode())
                .isEqualTo(RefType.SERVICE_REQUEST.getCode());
        assertThat(auditCaptor.getValue().relatedReferenceSn()).isEqualTo(41L);
    }
}
