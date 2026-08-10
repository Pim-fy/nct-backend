package nct.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

// @ai_generated
class ProductMapperContractTest {

    @Test
    @DisplayName("내 판매 목록은 예약 배지 판정에 필요한 draft 필드를 반환한다")
    void myProductsSelectsDraftFields() throws IOException {
        String findMyProducts = loadFindMyProductsSql();

        assertThat(findMyProducts)
                .contains("p.PRD_DRAFT_BID_UNIT, p.PRD_DRAFT_START_DT, p.PRD_DRAFT_END_DT,")
                .contains("p.PRD_DRAFT_START_NOW_YN, p.PRD_DRAFT_POLICY_AGREED_YN,");
    }

    @Test
    @DisplayName("내 판매 목록은 예약 조건을 draft 보존 컬럼과 미래 시작시각으로 판정한다")
    void reservedFilterUsesDraftReservationFields() throws IOException {
        String findMyProducts = loadFindMyProductsSql();

        assertThat(findMyProducts)
                .contains("<when test=\"filterType == 'RESERVED'\">")
                .contains("p.PRD_STATUS_CD = 'PRDC0001'")
                .contains("p.PRD_DRAFT_START_NOW_YN = 'N'")
                .contains("p.PRD_DRAFT_START_DT > NOW()");
    }

    @Test
    @DisplayName("임시저장 필터는 예약 부분집합을 NULL 안전하게 제외한다")
    void draftFilterExcludesReservedSubsetNullSafely() throws IOException {
        String findMyProducts = loadFindMyProductsSql();

        assertThat(findMyProducts)
                .contains("<when test=\"filterType == 'DRAFT'\">")
                .contains("COALESCE(p.PRD_DRAFT_START_NOW_YN, '') != 'N'")
                .contains("p.PRD_DRAFT_START_DT IS NULL")
                .contains("p.PRD_DRAFT_START_DT &lt;= NOW()");
    }

    @Test
    @DisplayName("내 판매 목록은 낙찰 직후 거래가 없는 상품을 거래중에 포함하고 낙찰 분기는 제거한다")
    void tradingFilterAbsorbsWonTransition() throws IOException {
        String findMyProducts = loadFindMyProductsSql();

        assertThat(findMyProducts)
                .contains("t.TRD_STATUS_CD IN ('TRDC0003', 'TRDC0004', 'TRDC0005')")
                .contains("a.AUC_STATUS_CD = 'AUCC0003' AND t.TRD_SN IS NULL")
                .doesNotContain("<when test=\"filterType == 'WON'\">");
    }

    private String loadFindMyProductsSql() throws IOException {
        ClassPathResource mapperResource = new ClassPathResource("mapper/product/ProductMapper.xml");
        try (var inputStream = mapperResource.getInputStream()) {
            String mapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
            int elementStart = mapperXml.indexOf("<select id=\"findMyProducts\"");
            int elementEnd = mapperXml.indexOf("</select>", elementStart);
            assertThat(elementStart).isGreaterThanOrEqualTo(0);
            assertThat(elementEnd).isGreaterThan(elementStart);
            return mapperXml.substring(elementStart, elementEnd);
        }
    }
}
