package nct.point.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import nct.global.response.PageResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.point.domain.PointExchangeOrder;
import nct.point.dto.AdminPointExchangeAccountResponse;
import nct.point.service.PointExchangeService;

/** 담당자 7 · F-PAY-012: 관리자 환전 조회 컨트롤러의 인증·검색 전달값을 검증합니다. */
class AdminPointExchangeControllerTest {

    @Test
    void forwardsAdminOrderSearchConditions() {
        PointExchangeService service = mock(PointExchangeService.class);
        AdminPointExchangeController controller = new AdminPointExchangeController(service);
        PageResponse<PointExchangeOrder> page = PageResponse.<PointExchangeOrder>builder()
                .content(List.of())
                .totalCount(0)
                .page(2)
                .size(20)
                .hasNext(false)
                .build();
        when(service.getAdminOrderPage("PEOC0002", "회원", 2, 20)).thenReturn(page);

        controller.getOrders("PEOC0002", "회원", 2, 20);

        verify(service).getAdminOrderPage("PEOC0002", "회원", 2, 20);
    }

    @Test
    void forwardsAuthenticatedAdminAndIpForAccountReveal() {
        PointExchangeService service = mock(PointExchangeService.class);
        AdminPointExchangeController controller = new AdminPointExchangeController(service);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AdminPointExchangeAccountResponse response = new AdminPointExchangeAccountResponse(
                12L,
                "국민은행",
                "123-45-6789");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(service.getRequestedAccountForAdmin(12L, 7L, "127.0.0.1")).thenReturn(response);

        controller.getAccount(12L, adminUserDetails(7L), request);

        verify(service).getRequestedAccountForAdmin(12L, 7L, "127.0.0.1");
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
