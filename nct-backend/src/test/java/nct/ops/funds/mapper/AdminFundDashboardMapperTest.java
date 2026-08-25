package nct.ops.funds.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import nct.ops.funds.dto.AdminFundSnapshot;
import nct.support.SafeSpringBootIntegrationTest;

@SpringBootTest
@Transactional(readOnly = true)
class AdminFundDashboardMapperTest extends SafeSpringBootIntegrationTest {

    @Autowired
    private AdminFundDashboardMapper fundDashboardMapper;

    @Test
    void fundSummaryQueriesRunAgainstCurrentSchema() {
        AdminFundSnapshot snapshot = fundDashboardMapper.findSnapshot();
        LocalDateTime endAt = LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getAttentionHoldAmount()).isGreaterThanOrEqualTo(0L);
        assertThat(snapshot.getAttentionHoldCount()).isGreaterThanOrEqualTo(0L);
        assertThat(snapshot.getActiveEscrowAmount()).isGreaterThanOrEqualTo(0L);
        assertThat(fundDashboardMapper.findDailyFlows(endAt.minusDays(7), endAt))
                .isNotNull()
                .allSatisfy(flow -> {
                    assertThat(flow.getAuctionTradeAmount()).isGreaterThanOrEqualTo(0L);
                    assertThat(flow.getServiceTradeAmount()).isGreaterThanOrEqualTo(0L);
                });
    }
}
