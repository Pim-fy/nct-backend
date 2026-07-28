package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AuctionSellerHistoryMapperContractTest {

    @Test
    void sellerHistoryUsesSellerIdAndIncludesEveryAuctionStatus() throws IOException {
        ClassPathResource mapperResource = new ClassPathResource("mapper/auction/AuctionMapper.xml");
        String mapperXml;

        try (var inputStream = mapperResource.getInputStream()) {
            mapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(mapperXml)
                .contains("p.USR_SN = #{condition.sellerId}")
                .contains("condition.sellerId != null and condition.includeHistory")
                .contains("'AUCC0001', 'AUCC0002', 'AUCC0003'")
                .contains("'AUCC0004', 'AUCC0005', 'AUCC0006'");
    }
}
