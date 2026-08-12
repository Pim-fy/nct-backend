package nct.ops.servicequery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import nct.ops.servicequery.service.AdminServiceRequestOperationService;
import nct.ops.servicequery.service.AdminServiceRequestQueryService;

/** 담당자 7: 관리자 서비스 요청 상세 번호가 서비스 계층으로 전달되는지 검증한다. */
class AdminServiceRequestQueryControllerTest {

    @Test
    void forwardsServiceRequestIdToService() {
        AdminServiceRequestQueryService service = mock(AdminServiceRequestQueryService.class);
        AdminServiceRequestQueryController controller = new AdminServiceRequestQueryController(
                service,
                mock(AdminServiceRequestOperationService.class));

        var response = controller.getDetail(1256L);

        verify(service).getDetail(1256L);
        assertThat(response.getBody().getStatus()).isEqualTo("success");
    }
}
