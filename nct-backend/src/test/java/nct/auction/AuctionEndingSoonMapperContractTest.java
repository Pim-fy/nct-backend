package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AuctionEndingSoonMapperContractTest {

    @Test
    @DisplayName("마감 임박 목록 조건은 종료 24시간 이내로 제한한다")
    void endingSoonFiltersUseTwentyFourHourWindow() throws IOException {
        ClassPathResource mapperResource = new ClassPathResource("mapper/auction/AuctionMapper.xml");
        String mapperXml;

        try (var inputStream = mapperResource.getInputStream()) {
            mapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(mapperXml)
                .contains("DATE_ADD(NOW(), INTERVAL 24 HOUR)")
                .doesNotContain("DATE_ADD(NOW(), INTERVAL 1 HOUR)");
        assertThat(countOccurrences(mapperXml, "DATE_ADD(NOW(), INTERVAL 24 HOUR)"))
                .isEqualTo(2);
    }

    private int countOccurrences(String source, String target) {
        return (source.length() - source.replace(target, "").length()) / target.length();
    }
}
