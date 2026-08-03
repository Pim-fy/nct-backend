package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AuctionTradeMethodMapperContractTest {

    @Test
    @DisplayName("현재 최고입찰 거래방식은 본인 상세에만 노출하고 조건부 갱신한다")
    void currentHighestTradeMethodUsesAuthenticatedBidderAndConditionalUpdate() throws IOException {
        ClassPathResource mapperResource = new ClassPathResource("mapper/auction/AuctionMapper.xml");
        String mapperXml;

        try (var inputStream = mapperResource.getInputStream()) {
            mapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        String normalizedXml = mapperXml.replaceAll("\\s+", " ");
        assertThat(normalizedXml)
                .contains("AND highestBid.USR_SN = #{userId}")
                .contains("END AS myBidTradeMethodCode")
                .contains("<update id=\"updateCurrentHighestBidTradeMethod\">")
                .contains("AND BID_SN = #{bidId}")
                .contains("AND USR_SN = #{userId}")
                .contains("AND BID_STATUS_CD = 'BIDC0001'")
                .contains("BID_TRD_METHOD_CD IS NULL OR BID_TRD_METHOD_CD &lt;&gt; #{tradeMethodCode}");
    }
}
