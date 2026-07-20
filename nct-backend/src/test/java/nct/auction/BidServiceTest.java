package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.dto.MyBidHistoryItem;
import nct.auction.service.BidService;

@SpringBootTest
@Transactional
class BidServiceTest {

    @Autowired BidService bidService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("내 입찰 내역: 상태 코드와 경매 표시 정보를 함께 조회한다")
    void getMyBidHistory() {
        long sellerSn = insertUser("t_bid_seller");
        long bidderSn = insertUser("t_bid_bidder");
        long prdSn = insertProduct(sellerSn, "테스트 입찰 상품");
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE, BigDecimal.valueOf(85000));
        long bidSn = insertBid(aucSn, bidderSn, BigDecimal.valueOf(80000), BidStatusCode.HIGHEST);

        List<MyBidHistoryItem> history = bidService.getMyBidHistory(bidderSn);

        assertThat(history).hasSize(1);
        MyBidHistoryItem item = history.get(0);
        assertThat(item.getBidSn()).isEqualTo(bidSn);
        assertThat(item.getAucSn()).isEqualTo(aucSn);
        assertThat(item.getPrdSn()).isEqualTo(prdSn);
        assertThat(item.getAuctionTitle()).isEqualTo("테스트 입찰 상품");
        assertThat(item.getBidAmount()).isEqualByComparingTo("80000");
        assertThat(item.getCurrentPrice()).isEqualByComparingTo("85000");
        assertThat(item.getBidStatusCode()).isEqualTo(BidStatusCode.HIGHEST);
        assertThat(item.getAuctionStatusCode()).isEqualTo(AuctionStatusCode.ACTIVE);
        assertThat(item.getDisplayStatus()).isEqualTo("HIGHEST");
        assertThat(item.getAuctionEndDateTime()).isNotNull();
        assertThat(item.getSellerSn()).isEqualTo(sellerSn);
        assertThat(item.getSellerName()).isEqualTo("t_bid_seller");
    }

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, loginId + "@test.local");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertProduct(long sellerSn, String productName) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, ?, 'PRDC0002', 10000,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn, productName);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAuction(long prdSn, String statusCode, BigDecimal currentAmount) {
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
                statusCode,
                currentAmount,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertBid(long aucSn, long bidderSn, BigDecimal amount, String statusCode) {
        jdbc.update("""
                INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD)
                VALUES (?, ?, ?, ?)
                """, aucSn, bidderSn, amount, statusCode);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
