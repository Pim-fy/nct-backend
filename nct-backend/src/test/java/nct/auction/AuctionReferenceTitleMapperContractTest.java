package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · F-COM-018: 신고용 경매 제목 배치 조회가 상품 활성 상태와 무관하게 유지되는지 검증합니다. */
class AuctionReferenceTitleMapperContractTest {

    @Test
    void selectsAuctionProductTitleInOneBatchWithoutUseYnFilter() throws IOException {
        String mapper = new ClassPathResource("mapper/auction/AuctionMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
        int start = mapper.indexOf("<select id=\"findAuctionReferenceTitles\"");
        String query = mapper.substring(start, mapper.indexOf("</select>", start));

        assertThat(query)
                .contains("JOIN PRODUCT p ON p.PRD_SN = a.PRD_SN")
                .contains("p.PRD_NM AS title")
                .contains("a.AUC_SN IN")
                .doesNotContain("PRD_USE_YN")
                .doesNotContain("USR_STATUS_CD");
    }
}
