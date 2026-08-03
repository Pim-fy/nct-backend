package nct.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.audit.service.AuditLogService;
import nct.global.security.crypto.FieldCryptoService;
import nct.notification.service.NotificationService;
import nct.point.mapper.PointExchangeOrderMapper;

/** 담당자 7 · F-OPS-010: 대시보드 집계가 환전 계좌정보를 복호화하지 않는지 검증합니다. */
class PointExchangeServiceDashboardTest {

    @Test
    void countsRequestedOrdersWithoutDecryptingAccountData() {
        PointExchangeOrderMapper exchangeMapper = mock(PointExchangeOrderMapper.class);
        PointService pointService = mock(PointService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        FieldCryptoService fieldCryptoService = mock(FieldCryptoService.class);
        when(exchangeMapper.countRequestedForAdmin()).thenReturn(7L);

        PointExchangeService service = new PointExchangeService(
                exchangeMapper,
                pointService,
                notificationService,
                auditLogService,
                fieldCryptoService);

        assertThat(service.countRequestedForAdmin()).isEqualTo(7L);
        verify(exchangeMapper).countRequestedForAdmin();
        verifyNoInteractions(fieldCryptoService);
    }
}
