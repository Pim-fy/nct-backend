package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.auction.dto.AuctionStatusResponse;
import nct.auction.service.AuctionService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

@SpringBootTest
@Transactional
class AuctionServiceTest {

    @Autowired AuctionService auctionService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("상품 번호로 경매 현황을 조회한다")
    void getAuctionStatusByProduct() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(15000));
        insertBid(aucSn, bidderSn, BigDecimal.valueOf(15000));
        insertBid(aucSn, bidderSn, BigDecimal.valueOf(17000));

        AuctionStatusResponse response = auctionService.getAuctionStatusByProduct(prdSn);

        assertThat(response.getAucSn()).isEqualTo(aucSn);
        assertThat(response.getPrdSn()).isEqualTo(prdSn);
        assertThat(response.getAucStatusCd()).isEqualTo("AUCC0002");
        assertThat(response.getAucCurAmt()).isEqualByComparingTo("15000");
        assertThat(response.getBidCount()).isEqualTo(2);
        assertThat(response.getAucStartDt()).isNotNull();
        assertThat(response.getAucEndDt()).isNotNull();
        assertThat(response.getAucExtCnt()).isZero();
    }

    @Test
    @DisplayName("해당 상품의 경매가 없으면 AUCTION_NOT_FOUND가 발생한다")
    void getAuctionStatusByProductNotFound() {
        long sellerSn = insertUser("t_auc_seller");
        long prdSn = insertProduct(sellerSn);

        assertThatThrownBy(() -> auctionService.getAuctionStatusByProduct(prdSn))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);
    }

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, loginId + "@test.local");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertProduct(long sellerSn) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '테스트 경매 상품', 'PRDC0002', 10000,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAuction(long prdSn, BigDecimal currentAmount) {
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
                VALUES (?, 'AUCC0002', ?, 1000, ?, ?, 0)
                """,
                prdSn,
                currentAmount,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertBid(long aucSn, long bidderSn, BigDecimal amount) {
        jdbc.update("""
                INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD)
                VALUES (?, ?, ?, 'BIDC0002')
                """, aucSn, bidderSn, amount);
    }
}
