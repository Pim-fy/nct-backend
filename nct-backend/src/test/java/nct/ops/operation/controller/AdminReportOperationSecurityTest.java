package nct.ops.operation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import nct.ops.operation.domain.AdminDisputeDecision;
import nct.ops.operation.domain.ReportEnforcementAction;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.service.AdminReportOperationService;
import nct.support.SafeSpringBootIntegrationTest;

/** 담당자 7 · F-OPS-007/F-OPS-018: 신고 판정 API의 실제 관리자 권한 경계를 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReportOperationSecurityTest extends SafeSpringBootIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdminReportOperationService reportOperationService;
    @MockitoBean private RequestFingerprintMapper requestFingerprintMapper;

    @Test
    void enforcesAdminRoleAndForwardsAuthenticatedDecision() throws Exception {
        String body = """
                {
                  "decision":"PROCESSED",
                  "tradeDecision":"COMPLETE",
                  "enforcementAction":"NONE",
                  "reason":"거래 신고 조정 완료"
                }
                """;

        mockMvc.perform(post("/api/admin/reports/91/decision")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/reports/91/decision")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportOperationService);

        when(requestFingerprintMapper.tryInsert(any(), any())).thenReturn(1);
        when(requestFingerprintMapper.updateResponse(any(), any(Integer.class), any())).thenReturn(1);
        mockMvc.perform(post("/api/admin/reports/91/decision")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminUserDetails(7L),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        verify(reportOperationService).decide(
                eq(91L),
                eq(AdminReportDecision.PROCESSED),
                eq(AdminDisputeDecision.COMPLETE),
                eq(ReportEnforcementAction.NONE),
                eq("거래 신고 조정 완료"),
                eq(7L));
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
