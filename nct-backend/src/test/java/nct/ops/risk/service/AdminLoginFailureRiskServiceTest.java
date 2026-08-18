package nct.ops.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.global.security.crypto.FieldCryptoService;
import nct.ops.risk.port.AdminLoginFailureSignalStore;
import nct.ops.risk.port.RiskDetectionPolicy;
import nct.ops.risk.port.RiskDetectionPolicyReader;

class AdminLoginFailureRiskServiceTest {

    private AdminLoginFailureSignalStore signalStore;
    private RiskEventService riskEventService;
    private AdminLoginFailureRiskService service;

    @BeforeEach
    void setUp() {
        FieldCryptoService cryptoService = mock(FieldCryptoService.class);
        signalStore = mock(AdminLoginFailureSignalStore.class);
        RiskDetectionPolicyReader policyReader = mock(RiskDetectionPolicyReader.class);
        riskEventService = mock(RiskEventService.class);
        service = new AdminLoginFailureRiskService(
                cryptoService, signalStore, policyReader, riskEventService);
        when(cryptoService.hmac("ADMIN_LOGIN:admin01"))
                .thenReturn("aaaaaaaaaaaa11111111111111111111");
        when(cryptoService.hmac("ADMIN_IP:127.0.0.1"))
                .thenReturn("bbbbbbbbbbbb22222222222222222222");
        when(policyReader.getPolicy())
                .thenReturn(new RiskDetectionPolicy(10, 60, 7, 3, 7, 5, 10));
        when(riskEventService.recordOnceSince(any(), any()))
                .thenReturn(new RiskEventResult(1L, true));
    }

    @Test
    void createsPseudonymousRiskEventAtIdentityThreshold() {
        when(signalStore.countSince(
                org.mockito.ArgumentMatchers.eq("identityToken"), any(), any())).thenReturn(5L);
        when(signalStore.countSince(
                org.mockito.ArgumentMatchers.eq("ipToken"), any(), any())).thenReturn(1L);

        service.recordFailure(" Admin01 ", "127.0.0.1");

        verify(signalStore).record(
                "aaaaaaaaaaaa11111111111111111111",
                "bbbbbbbbbbbb22222222222222222222");
        ArgumentCaptor<RiskEventCommand> captor = ArgumentCaptor.forClass(RiskEventCommand.class);
        verify(riskEventService).recordOnceSince(captor.capture(), any());
        assertThat(captor.getValue().typeCode()).isEqualTo("RSKC0008");
        assertThat(captor.getValue().content())
                .contains("aaaaaaaaaaaa")
                .doesNotContain("admin01")
                .doesNotContain("127.0.0.1");
    }

    @Test
    void doesNotCreateRiskEventBelowThreshold() {
        when(signalStore.countSince(any(), any(), any()))
                .thenReturn(4L);

        service.recordFailure("admin01", "127.0.0.1");

        verify(riskEventService, never()).recordOnceSince(any(), any());
    }
}
