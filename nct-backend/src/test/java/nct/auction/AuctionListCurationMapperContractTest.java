package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AuctionListCurationMapperContractTest {

    private String normalizedMapperXml;

    @BeforeEach
    void loadMapperXml() throws IOException {
        ClassPathResource mapperResource = new ClassPathResource("mapper/auction/AuctionMapper.xml");

        try (var inputStream = mapperResource.getInputStream()) {
            normalizedMapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
        }
    }

    @Test
    @DisplayName("공개 경매 목록은 임시저장 상품을 제외한다")
    void publicAuctionListExcludesDraftProducts() {
        assertThat(normalizedMapperXml)
                .contains("p.PRD_USE_YN = 'Y'")
                .contains("p.PRD_STATUS_CD = 'PRDC0002'");
    }

    @Test
    @DisplayName("공개 경매 목록은 종료 시각이 지난 진행 상태 경매를 제외한다")
    void publicAuctionListExcludesExpiredActiveAuctions() {
        assertThat(normalizedMapperXml)
                .contains("a.AUC_STATUS_CD != 'AUCC0002' OR a.AUC_END_DT &gt; NOW()");
    }

    @Test
    @DisplayName("신규 경매 정렬은 등록일시가 최신인 경매부터 조회한다")
    void latestSortUsesAuctionRegistrationDate() {
        assertThat(normalizedMapperXml)
                .contains("condition.sort == 'latest'")
                .contains("ORDER BY a.AUC_REG_DT DESC, a.AUC_SN DESC");
    }
}
