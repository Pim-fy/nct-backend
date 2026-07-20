package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionListRequest;
import nct.auction.dto.AuctionListResponse;
import nct.auction.service.AuctionService;

@SpringBootTest
class AuctionListFilterTest {

    @Autowired AuctionService auctionService;
    @Autowired JdbcTemplate jdbc;

    final List<Long> userIds = new ArrayList<>();
    final List<Long> productIds = new ArrayList<>();
    final List<Long> auctionIds = new ArrayList<>();

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
        if (!userIds.isEmpty()) {
            String ids = joinIds(userIds);
            jdbc.update("DELETE FROM NOTIFICATION WHERE USR_SN IN (" + ids + ")");
            jdbc.update("DELETE FROM USERS WHERE USR_SN IN (" + ids + ")");
        }
    }

    @Test
    @DisplayName("즉시구매 필터가 없으면 즉시구매가 null, 0, 양수 상품을 모두 조회한다")
    void findAuctionsWithoutInstantBuyFilter() {
        long sellerSn = insertUser("t_iby_seller");
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
        long sellerSn = insertUser("t_iby_seller");
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
        long sellerSn = insertUser("t_iby_seller");
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

    private AuctionListRequest keywordRequest(String keyword) {
        AuctionListRequest request = new AuctionListRequest();
        request.setKeyword(keyword);
        request.setSort("latest");
        request.setSize(20);
        return request;
    }

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, loginId + "@test.local");
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        userIds.add(id);
        return id;
    }

    private long insertAuction(long sellerSn, String productName, BigDecimal instantBuyPrice, BigDecimal currentAmount) {
        long prdSn = insertProduct(sellerSn, productName, instantBuyPrice);
        jdbc.update("""
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
                AuctionStatusCode.ACTIVE,
                currentAmount,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));
        long aucSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auctionIds.add(aucSn);
        return aucSn;
    }

    private long insertProduct(long sellerSn, String productName, BigDecimal instantBuyPrice) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, ?, 'PRDC0002', 10000, ?,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn, productName, instantBuyPrice);
        long prdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        productIds.add(prdSn);
        return prdSn;
    }

    private String joinIds(List<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }
}
