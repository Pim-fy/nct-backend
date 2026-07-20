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
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.service.AuctionService;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.point.domain.PointBalance;
import nct.point.service.PointService;

@SpringBootTest
@Transactional
class AuctionServiceTest {

    @Autowired AuctionService auctionService;
    @Autowired PointService pointService;
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

    @Test
    @DisplayName("입찰 성공 시 신규 입찰자의 포인트를 홀딩한다")
    void placeBidHoldsBidderPoint() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));
        creditAvailable(bidderSn, 50000);

        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(12000));

        auctionService.placeBid(aucSn, bidderSn, request);

        long bidSn = latestBidSn(aucSn);
        PointBalance balance = pointService.getBalance(bidderSn);
        assertThat(balance.getAvailableAmt()).isEqualTo(38000);
        assertThat(balance.getHoldAmt()).isEqualTo(12000);
        assertThat(activeHoldAmount(bidderSn, bidSn)).isEqualTo(12000);
    }

    @Test
    @DisplayName("상위 입찰 성공 시 기존 최고입찰자의 홀딩을 반환한다")
    void placeBidReleasesPreviousHighestBidHold() {
        long sellerSn = insertUser("t_auc_seller");
        long firstBidderSn = insertUser("t_auc_first_bidder");
        long secondBidderSn = insertUser("t_auc_second_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(12000));
        long firstBidSn = insertBid(aucSn, firstBidderSn, BigDecimal.valueOf(12000), "BIDC0001");
        creditAvailable(firstBidderSn, 50000);
        creditAvailable(secondBidderSn, 50000);
        pointService.hold(firstBidderSn, 12000, RefType.BID, firstBidSn, "기존 입찰 홀딩");

        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(14000));

        auctionService.placeBid(aucSn, secondBidderSn, request);

        PointBalance firstBalance = pointService.getBalance(firstBidderSn);
        PointBalance secondBalance = pointService.getBalance(secondBidderSn);
        assertThat(firstBalance.getAvailableAmt()).isEqualTo(50000);
        assertThat(firstBalance.getHoldAmt()).isZero();
        assertThat(secondBalance.getAvailableAmt()).isEqualTo(36000);
        assertThat(secondBalance.getHoldAmt()).isEqualTo(14000);
    }

    @Test
    @DisplayName("즉시구매 성공 시 구매자 홀딩을 보관금으로 전환한다")
    void buyNowConvertsHoldToEscrow() {
        long sellerSn = insertUser("t_auc_seller");
        long buyerSn = insertUser("t_auc_buyer");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));
        creditAvailable(buyerSn, 50000);

        auctionService.buyNow(aucSn, buyerSn, new AuctionBuyNowRequest());

        long bidSn = latestBidSn(aucSn);
        PointBalance balance = pointService.getBalance(buyerSn);
        assertThat(balance.getAvailableAmt()).isEqualTo(20000);
        assertThat(balance.getHoldAmt()).isZero();
        assertThat(activeHoldAmount(buyerSn, bidSn)).isZero();
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
        return insertProduct(sellerSn, null);
    }

    private long insertProduct(long sellerSn, BigDecimal instantBuyAmount) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '테스트 경매 상품', 'PRDC0002', 10000, ?,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn, instantBuyAmount);
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
        insertBid(aucSn, bidderSn, amount, "BIDC0002");
    }

    private long insertBid(long aucSn, long bidderSn, BigDecimal amount, String statusCode) {
        jdbc.update("""
                INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD)
                VALUES (?, ?, ?, ?)
                """, aucSn, bidderSn, amount, statusCode);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void creditAvailable(long usrSn, long amount) {
        jdbc.update("""
                INSERT INTO POINT_LEDGER (
                    USR_SN,
                    PT_LDG_PT_TYPE_CD,
                    PT_LDG_TYPE_CD,
                    PT_LDG_AMT,
                    PT_LDG_BAL_AFTER_AMT,
                    PT_LDG_RSN_CN
                )
                VALUES (?, 'PTLC0001', 'PTLC0004', ?, ?, '테스트 충전')
                """, usrSn, amount, amount);
    }

    private long latestBidSn(long aucSn) {
        return jdbc.queryForObject("""
                SELECT BID_SN
                FROM BID
                WHERE AUC_SN = ?
                ORDER BY BID_SN DESC
                LIMIT 1
                """, Long.class, aucSn);
    }

    private long activeHoldAmount(long usrSn, long bidSn) {
        Long amount = jdbc.queryForObject("""
                SELECT COALESCE(SUM(PT_LDG_AMT), 0)
                FROM POINT_LEDGER
                WHERE USR_SN = ?
                  AND PT_LDG_PT_TYPE_CD = 'PTLC0002'
                  AND PT_LDG_REF_TYPE_CD = ?
                  AND PT_LDG_REF_SN = ?
                """, Long.class, usrSn, RefType.BID.getCode(), bidSn);
        return amount == null ? 0 : amount;
    }
}
