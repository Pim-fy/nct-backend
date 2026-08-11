package nct.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · F-COM-008: 받은 물품 리뷰 역할 필터의 목록·개수 SQL 계약을 고정한다. */
class ReviewReceivedRoleMapperContractTest {

    @Test
    void sellerAndBuyerFiltersUseTheReviewedUsersTradeRole() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");

        assertThat(mapper)
                .contains("<sql id=\"receivedGoodsRoleFilter\">")
                .contains("role != null and role == 'SELLER'")
                .contains("T.SLLR_USR_SN = R.REVWD_USR_SN")
                .contains("role != null and role == 'BUYER'")
                .contains("T.BYPR_USR_SN = R.REVWD_USR_SN");
    }

    @Test
    void listAndCountQueriesShareTheSameRoleFilter() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");

        assertThat(mapper)
                .containsPattern("(?s)<select id=\"selectReviewsByReceiver\".*?"
                        + "<include refid=\"receivedGoodsRoleFilter\"/>.*?</select>")
                .containsPattern("(?s)<select id=\"countReviewsByReceiver\".*?"
                        + "<include refid=\"receivedGoodsRoleFilter\"/>.*?</select>");
    }

    @Test
    void combinedTrustScoreRemainsIndependentFromRoleFilter() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");
        String trustScoreQuery = mapper.substring(
                mapper.indexOf("<select id=\"selectTrustScore\""),
                mapper.indexOf("</select>", mapper.indexOf("<select id=\"selectTrustScore\"")));

        assertThat(trustScoreQuery)
                .contains("AS goodsCount")
                .contains("AS goodsScore")
                .doesNotContain("receivedGoodsRoleFilter");
    }

    private String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
