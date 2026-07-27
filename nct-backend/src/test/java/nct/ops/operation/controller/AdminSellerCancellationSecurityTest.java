package nct.ops.operation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import nct.global.idempotency.RequestFingerprintMapper;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.port.SellerCancellationDecisionCommand;
import nct.trade.service.TradeService;

/** 담당자 7 · F-OPS-004: 판매자 취소 판정 API의 관리자 권한 경계를 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminSellerCancellationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeService tradeService;

    @MockitoBean
    private RequestFingerprintMapper requestFingerprintMapper;

    @MockitoBean
    private AuditLogPort auditLogPort;

    @Test
    void rejectsAnonymousUser() throws Exception {
        mockMvc.perform(post("/api/admin/seller-cancellations/91/decision")
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVED","reason":"seller policy cancellation approved"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));

        verifyNoInteractions(tradeService);
    }

    @Test
    void rejectsAuthenticatedNonAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/seller-cancellations/91/decision")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVED","reason":"seller policy cancellation approved"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.httpCode").value(403));

        verifyNoInteractions(tradeService);
    }

    @Test
    void allowsAdminAndCallsDecisionPort() throws Exception {
        when(requestFingerprintMapper.tryInsert(any(), any())).thenReturn(1);
        when(requestFingerprintMapper.updateResponse(any(), any(Integer.class), any())).thenReturn(1);

        mockMvc.perform(post("/api/admin/seller-cancellations/91/decision")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminUserDetails(7L),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                        .contentType("application/json")
                        .content("""
                                {"decision":"REJECTED","reason":"insufficient evidence"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(tradeService).decide(any(SellerCancellationDecisionCommand.class));
        verify(auditLogPort).record(any(AuditLogCommand.class));
    }

    private CustomUserDetails adminUserDetails(Long userId) {
        return new CustomUserDetails(AuthMember.builder()
                .id(userId)
                .email("admin@example.com")
                .password("{noop}test")
                .role("ROLE_ADMIN")
                .status("USRC0001")
                .build());
    }
}
