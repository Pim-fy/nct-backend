package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionListRequest;
import nct.auction.dto.AuctionListResponse;
import nct.auction.service.AuctionService;
import nct.global.security.crypto.FieldCryptoService;
import nct.support.ApprovedDatabaseWriteIntegrationTest;
import nct.support.TestGeneratedKeys;

@SpringBootTest
class AuctionListFilterTest extends ApprovedDatabaseWriteIntegrationTest {

    @Autowired AuctionService auctionService;
    @Autowired JdbcTemplate jdbc;
    @Autowired FieldCryptoService fieldCryptoService;

    final List<Long> productIds = new ArrayList<>();
    final List<Long> auctionIds = new ArrayList<>();
    long sellerSn;

    @BeforeEach
    void setUpSeller() {
        String loginId = "t_auclist_" + System.nanoTime();
        String email = loginId + "@test.local";
        sellerSn = TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML_ENC, USR_EML_HMAC, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId, fieldCryptoService.encrypt(email), fieldCryptoService.emailHmac(email));
    }

    @AfterEach
    void cleanUp() {
        if (!auctionIds.isEmpty()) {
            jdbc.update("DELETE FROM BID WHERE AUC_SN IN (" + joinIds(auctionIds) + ")");
            jdbc.update("DELETE FROM AUCTION WHERE AUC_SN IN (" + joinIds(auctionIds) + ")");
        }
        if (!productIds.isEmpty()) {
            jdbc.update("DELETE FROM PRODUCT_FAVORITE WHERE PRD_SN IN (" + joinIds(productIds) + ")");
            jdbc.update("DELETE FROM PRODUCT WHERE PRD_SN IN (" + joinIds(productIds) + ")");
        }
        jdbc.update("DELETE FROM USERS WHERE USR_SN = ?", sellerSn);
    }

    @Test
    @DisplayName("즉시구매 필터가 없으면 즉시구매가 null, 0, 양수 상품을 모두 조회한다")
    void findAuctionsWithoutInstantBuyFilter() {
        insertAuction(sellerSn, "t_iby_all_null", null, BigDecimal.valueOf(10000));
        insertAuction(sellerSn, "t_iby_all_zero", BigDecimal.ZERO, BigDecimal.valueOf(12000));
        insertAuction(sellerSn, "t_iby_all_positive", BigDecimal.valueOf(50000), BigDecimal.valueOf(14000));

        AuctionListRequest request = keywordRequest("t_iby_all");

        AuctionListResponse response = auctionService.findAuctions(request);

        assertThat(response.getItems()).extracting("title")
                .containsExactlyInAnyOrder("t_iby_all_null", "t_iby_all_zero", "t_iby_all_positive");
    }

    @Test
    @DisplayName("즉시구매 필터는 즉시구매가가 있는 상품만 반환하고 null과 0원을 제외한다")
    void findAuctionsWithInstantBuyOnly() {
        insertAuction(sellerSn, "t_iby_only_null", null, BigDecimal.valueOf(10000));
        insertAuction(sellerSn, "t_iby_only_zero", BigDecimal.ZERO, BigDecimal.valueOf(12000));
        insertAuction(sellerSn, "t_iby_only_positive", BigDecimal.valueOf(50000), BigDecimal.valueOf(14000));

        AuctionListRequest request = keywordRequest("t_iby_only");
        request.setInstantBuyOnly(true);

        AuctionListResponse response = auctionService.findAuctions(request);

        assertThat(response.getItems()).extracting("title")
                .containsExactly("t_iby_only_positive");
    }

    @Test
    @DisplayName("즉시구매 필터와 가격 필터를 동시에 적용한다")
    void findAuctionsWithInstantBuyOnlyAndPriceFilter() {
        insertAuction(sellerSn, "t_iby_price_low", BigDecimal.valueOf(30000), BigDecimal.valueOf(10000));
        insertAuction(sellerSn, "t_iby_price_match", BigDecimal.valueOf(60000), BigDecimal.valueOf(45000));
        insertAuction(sellerSn, "t_iby_price_no_instant", null, BigDecimal.valueOf(50000));

        AuctionListRequest request = keywordRequest("t_iby_price");
        request.setInstantBuyOnly(true);
        request.setMinPrice(BigDecimal.valueOf(40000));

        AuctionListResponse response = auctionService.findAuctions(request);

        assertThat(response.getItems()).extracting("title")
                .containsExactly("t_iby_price_match");
    }

    @Test
    @DisplayName("선택한 거래가 가능한 경매를 거래방식 공통코드로 필터링한다")
    void findAuctionsWithReferenceCodeFilters() {
        insertAuction(
                sellerSn,
                "t_reference_ready_delivery",
                null,
                BigDecimal.valueOf(10000),
                AuctionStatusCode.READY,
                "TRDC0009");
        insertAuction(
                sellerSn,
                "t_reference_active_direct",
                null,
                BigDecimal.valueOf(12000),
                AuctionStatusCode.ACTIVE,
                "TRDC0010");
        insertAuction(
                sellerSn,
                "t_reference_active_both",
                null,
                BigDecimal.valueOf(14000),
                AuctionStatusCode.ACTIVE,
                "TRDC0020");

        AuctionListRequest request = keywordRequest("t_reference");
        request.setTradeMethod("delivery");

        AuctionListResponse response = auctionService.findAuctions(request);

        assertThat(response.getItems()).extracting("title")
                .containsExactlyInAnyOrder(
                        "t_reference_ready_delivery",
                        "t_reference_active_both");

        AuctionListRequest directRequest = keywordRequest("t_reference");
        directRequest.setTradeMethod("direct");

        assertThat(auctionService.findAuctions(directRequest).getItems())
                .extracting("title")
                .containsExactlyInAnyOrder(
                        "t_reference_active_direct",
                        "t_reference_active_both");

        AuctionListRequest allRequest = keywordRequest("t_reference");

        assertThat(auctionService.findAuctions(allRequest).getItems())
                .extracting("title")
                .containsExactlyInAnyOrder(
                        "t_reference_ready_delivery",
                        "t_reference_active_direct",
                        "t_reference_active_both");
    }

    private AuctionListRequest keywordRequest(String keyword) {
        AuctionListRequest request = new AuctionListRequest();
        request.setKeyword(keyword);
        request.setSort("latest");
        request.setSize(20);
        return request;
    }

    private long insertAuction(long sellerSn, String productName, BigDecimal instantBuyPrice, BigDecimal currentAmount) {
        return insertAuction(
                sellerSn,
                productName,
                instantBuyPrice,
                currentAmount,
                AuctionStatusCode.ACTIVE,
                "TRDC0009");
    }

    private long insertAuction(
            long sellerSn,
            String productName,
            BigDecimal instantBuyPrice,
            BigDecimal currentAmount,
            String auctionStatusCode,
            String tradeMethodCode) {
        long prdSn = insertProduct(sellerSn, productName, instantBuyPrice, tradeMethodCode);
        long aucSn = TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO AUCTION (
                    PRD_SN,
                    AUC_STATUS_CD,
                    AUC_CUR_AMT,
                    AUC_BID_UNIT_AMT,
                    AUC_START_DT,
                    AUC_END_DT,
                    AUC_EXT_CNT
                )
                VALUES (?, ?, ?, 1000, ?, ?, 0)
                """,
                prdSn,
                auctionStatusCode,
                currentAmount,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));
        auctionIds.add(aucSn);
        return aucSn;
    }

    private long insertProduct(long sellerSn, String productName, BigDecimal instantBuyPrice) {
        return insertProduct(sellerSn, productName, instantBuyPrice, "TRDC0009");
    }

    private long insertProduct(
            long sellerSn,
            String productName,
            BigDecimal instantBuyPrice,
            String tradeMethodCode) {
        long prdSn = TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, ?, 'PRDC0002', 10000, ?, ?)
                """, sellerSn, productName, instantBuyPrice, tradeMethodCode);
        productIds.add(prdSn);
        return prdSn;
    }

    private String joinIds(List<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }
}
