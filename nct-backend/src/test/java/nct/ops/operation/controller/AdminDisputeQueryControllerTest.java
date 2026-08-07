package nct.ops.operation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import nct.ops.operation.service.AdminDisputeQueryService;

/** 담당자 7 · F-OPS-005: 관리자 권한 선언과 상세 번호 전달을 검증합니다. */
class AdminDisputeQueryControllerTest {

    @Test
    void declaresExactAdminAuthority() {
        PreAuthorize annotation = AdminDisputeQueryController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('ROLE_ADMIN')");
    }

    @Test
    void forwardsDisputeNumberToService() {
        AdminDisputeQueryService service = mock(AdminDisputeQueryService.class);
        AdminDisputeQueryController controller = new AdminDisputeQueryController(service);

        var response = controller.getDetail(81L);

        verify(service).getDetail(81L);
        assertThat(response.getBody().getStatus()).isEqualTo("success");
    }
}
