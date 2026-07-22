package nct.ops.operation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;

import nct.global.exception.GlobalExceptionHandler;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.ops.operation.dto.AdminSellerCancellationDecisionRequest;
import nct.ops.operation.port.SellerCancellationDecision;
import nct.ops.operation.service.AdminSellerCancellationService;
import nct.ops.security.service.SensitiveDataMasker;

/** 담당자 7 · F-OPS-004: 판매자 취소 판정 컨트롤러 계약을 검증합니다. */
class AdminSellerCancellationControllerTest {

    private AdminSellerCancellationService adminSellerCancellationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminSellerCancellationService = mock(AdminSellerCancellationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminSellerCancellationController(adminSellerCancellationService))
                .setControllerAdvice(new GlobalExceptionHandler(new SensitiveDataMasker()))
                .build();
    }

    @Test
    void declaresExplicitPathVariableNameForStsCompilerCompatibility() throws Exception {
        Method method = AdminSellerCancellationController.class.getDeclaredMethod(
                "decide", Long.class, AdminSellerCancellationDecisionRequest.class, CustomUserDetails.class);

        assertThat(method.getParameters()[0].getAnnotation(PathVariable.class).name())
                .isEqualTo("tradeSn");
    }

    @Test
    void forwardsDecisionWhenPrincipalIsInjectedDirectly() {
        AdminSellerCancellationController controller = new AdminSellerCancellationController(adminSellerCancellationService);
        var request = new AdminSellerCancellationDecisionRequest();
        request.setDecision(SellerCancellationDecision.APPROVED);
        request.setReason(" seller policy cancellation approved ");

        controller.decide(91L, request, adminUserDetails(7L));

        verify(adminSellerCancellationService).decide(
                91L,
                SellerCancellationDecision.APPROVED,
                " seller policy cancellation approved ",
                7L);
    }

    @Test
    void rejectsInvalidRequestBeforeCallingPort() throws Exception {
        mockMvc.perform(post("/api/admin/seller-cancellations/91/decision")
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVED","reason":" "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminSellerCancellationService);
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
