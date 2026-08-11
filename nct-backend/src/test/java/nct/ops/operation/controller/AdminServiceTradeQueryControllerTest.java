package nct.ops.operation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import nct.ops.operation.service.AdminServiceTradeQueryService;

/** 담당자 7 · F-OPS-005: 관리자 거래 상세 API의 정확한 관리자 권한과 번호 전달을 검증합니다. */
class AdminServiceTradeQueryControllerTest {

    @Test
    void declaresExactAdminAuthority() {
        PreAuthorize annotation = AdminServiceTradeQueryController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('ROLE_ADMIN')");
    }

    @Test
    void forwardsTradeNumberToService() {
        AdminServiceTradeQueryService service = mock(AdminServiceTradeQueryService.class);
        AdminServiceTradeQueryController controller = new AdminServiceTradeQueryController(service);

        var response = controller.getDetail(91L);

        verify(service).getDetail(91L);
        assertThat(response.getBody().getStatus()).isEqualTo("success");
    }
}
