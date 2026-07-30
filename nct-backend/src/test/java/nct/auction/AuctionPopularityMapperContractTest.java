package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AuctionPopularityMapperContractTest {

    @Test
    @DisplayName("인기 순은 활성 관심 수에 가중치를 주고 조회수를 더해 정렬한다")
    void popularitySortUsesFavoriteAndViewScore() throws IOException {
        ClassPathResource mapperResource = new ClassPathResource("mapper/auction/AuctionMapper.xml");
        String mapperXml;

        try (var inputStream = mapperResource.getInputStream()) {
            mapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        String normalizedXml = mapperXml.replaceAll("\\s+", " ");
        assertThat(normalizedXml)
                .contains("WHERE PRD_FAV_USE_YN = 'Y'")
                .contains("condition.sort == 'popular'")
                .contains("(COALESCE(favorite.FAVORITE_COUNT, 0) * 100) + COALESCE(p.PRD_VIEW_CNT, 0) DESC")
                .contains("COALESCE(favorite.FAVORITE_COUNT, 0) DESC")
                .contains("COALESCE(p.PRD_VIEW_CNT, 0) DESC")
                .contains("a.AUC_SN DESC");
    }
}
