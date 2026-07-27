package nct.ops.operation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import nct.ops.operation.dto.AdminAuctionOverviewResponse;
import nct.ops.operation.service.AdminAuctionQueryService;

/** 담당자 7 · F-OPS-003: 관리자 경매 조회 컨트롤러 전달값을 검증합니다. */
class AdminAuctionQueryControllerTest {

    @Test
    void forwardsAuctionNumberToService() {
        AdminAuctionQueryService service = mock(AdminAuctionQueryService.class);
        AdminAuctionQueryController controller = new AdminAuctionQueryController(service);

        var response = controller.getAuctionOverview(81L);

        verify(service).getAuctionOverview(81L);
        assertThat(response.getBody().getStatus()).isEqualTo("success");
    }
}
