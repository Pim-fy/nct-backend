package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import nct.auction.dto.AuctionDetailResponse;
import nct.auction.dto.AuctionStatusResponse;
import nct.auction.dto.AuctionStatusSummaryResponse;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.service.AuctionService;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.point.domain.PointBalance;
import nct.point.exception.PointException;
import nct.point.service.PointService;

@SpringBootTest
@Transactional
class AuctionServiceTest {

    @Autowired AuctionService auctionService;
    @Autowired PointService pointService;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    @DisplayName("경매 상세 조회는 상품 서비스 계약으로 조회수를 증가시킨다")
    void findAuctionDetailIncreasesProductViewCount() {
        long sellerSn = insertUser("t_auc_seller");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));

        AuctionDetailResponse response = auctionService.findAuctionDetail(aucSn);

        assertThat(response.getProductId()).isEqualTo(prdSn);
        assertThat(response.getViewCount()).isEqualTo(1);
        assertThat(productViewCount(prdSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("경매 상세 조회는 로그인 사용자의 현재 최고입찰 여부를 반환한다")
    void findAuctionDetailReturnsCurrentHighestBidderStatus() {
        long sellerSn = insertUser("t_auc_seller");
        long highestBidderSn = insertUser("t_auc_highest_bidder");
        long otherBidderSn = insertUser("t_auc_other_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(12000));
        insertBid(aucSn, highestBidderSn, BigDecimal.valueOf(12000), "BIDC0001");

        AuctionDetailResponse highestBidderDetail = auctionService.findAuctionDetail(
                aucSn,
                highestBidderSn);
        AuctionDetailResponse otherBidderDetail = auctionService.findAuctionDetail(
                aucSn,
                otherBidderSn);
        AuctionDetailResponse anonymousDetail = auctionService.findAuctionDetail(aucSn);

        assertThat(highestBidderDetail.isCurrentHighestBidder()).isTrue();
        assertThat(otherBidderDetail.isCurrentHighestBidder()).isFalse();
        assertThat(anonymousDetail.isCurrentHighestBidder()).isFalse();
    }

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
    @DisplayName("상품 번호 목록으로 경매 상태를 일괄 조회한다")
    void getAuctionStatusesByProducts() {
        long sellerSn = insertUser("t_auc_seller");
        long activePrdSn = insertProduct(sellerSn);
        long canceledPrdSn = insertProduct(sellerSn);
        long noAuctionPrdSn = insertProduct(sellerSn);
        long activeAucSn = insertAuction(activePrdSn, BigDecimal.valueOf(10000), "AUCC0002");
        long canceledAucSn = insertAuction(canceledPrdSn, BigDecimal.valueOf(20000), "AUCC0005");

        List<AuctionStatusSummaryResponse> responses = auctionService.getAuctionStatusesByProducts(List.of(
                activePrdSn,
                canceledPrdSn,
                noAuctionPrdSn,
                activePrdSn));

        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(
                        AuctionStatusSummaryResponse::getPrdSn,
                        AuctionStatusSummaryResponse::getAucSn,
                        AuctionStatusSummaryResponse::getAucStatusCd,
                        AuctionStatusSummaryResponse::getAucStatusNm)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(activePrdSn, activeAucSn, "AUCC0002", "진행"),
                        org.assertj.core.groups.Tuple.tuple(canceledPrdSn, canceledAucSn, "AUCC0005", "취소"));
    }

    @Test
    @DisplayName("상품 번호 목록이 비어 있으면 빈 경매 상태 목록을 반환한다")
    void getAuctionStatusesByProductsEmpty() {
        assertThat(auctionService.getAuctionStatusesByProducts(List.of())).isEmpty();
        assertThat(auctionService.getAuctionStatusesByProducts(null)).isEmpty();
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
    @DisplayName("사용 가능 포인트가 부족하면 입찰과 경매 가격 변경을 롤백한다")
    void placeBidRollsBackWhenAvailablePointIsInsufficient() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));
        creditAvailable(bidderSn, 11000);

        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(12000));

        assertThatThrownBy(() -> auctionService.placeBid(aucSn, bidderSn, request))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_INSUFFICIENT);

        assertThat(isTransactionRollbackOnly()).isTrue();

        PointBalance balance = pointService.getBalance(bidderSn);
        assertThat(balance.getAvailableAmt()).isEqualTo(11000);
        assertThat(balance.getHoldAmt()).isZero();
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
    @DisplayName("즉시구매가 이상 금액은 일반 입찰로 등록할 수 없다")
    void placeBidRejectsBidAmountAtOrAboveInstantBuyPrice() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(15000));
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));
        creditAvailable(bidderSn, 50000);

        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(15000));

        assertThatThrownBy(() -> auctionService.placeBid(aucSn, bidderSn, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(bidCount(aucSn)).isZero();
        assertThat(pointService.getBalance(bidderSn).getHoldAmt()).isZero();
    }

    @Test
    @DisplayName("마감 10분 이내 최초 유효 입찰은 남은 시간을 10분으로 연장한다")
    void placeBidExtendsAuctionTimeNearDeadline() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(10000),
                LocalDateTime.now().plusMinutes(5),
                0);
        creditAvailable(bidderSn, 50000);

        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(12000));

        auctionService.placeBid(aucSn, bidderSn, request);

        assertThat(auctionRemainingSeconds(aucSn)).isBetween(8 * 60, 10 * 60 + 30);
        assertThat(auctionExtensionCount(aucSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 자동 연장된 경매는 마감 10분 이내 입찰이어도 다시 연장하지 않는다")
    void placeBidDoesNotExtendAuctionTimeMoreThanOnce() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(10000),
                LocalDateTime.now().plusMinutes(5),
                1);
        creditAvailable(bidderSn, 50000);

        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(12000));

        auctionService.placeBid(aucSn, bidderSn, request);

        assertThat(auctionRemainingSeconds(aucSn)).isLessThan(7 * 60);
        assertThat(auctionExtensionCount(aucSn)).isEqualTo(1);
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
        assertThat(materialTradeCount(prdSn)).isEqualTo(1);
        assertThat(deliverySnapshotCount(prdSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("사용 가능 포인트가 부족하면 즉시구매 전체 처리를 롤백한다")
    void buyNowRollsBackWhenAvailablePointIsInsufficient() {
        long sellerSn = insertUser("t_auc_seller");
        long buyerSn = insertUser("t_auc_buyer");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));
        creditAvailable(buyerSn, 20000);

        assertThatThrownBy(() -> auctionService.buyNow(aucSn, buyerSn, new AuctionBuyNowRequest()))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_INSUFFICIENT);

        assertThat(isTransactionRollbackOnly()).isTrue();

        PointBalance balance = pointService.getBalance(buyerSn);
        assertThat(balance.getAvailableAmt()).isEqualTo(20000);
        assertThat(balance.getHoldAmt()).isZero();
    }

    @Test
    @DisplayName("직거래 즉시구매는 거래만 생성하고 일정 저장 전에는 채팅방을 생성하지 않는다")
    void buyNowCreatesOfflineTradeWithoutChatRoom() {
        long sellerSn = insertUser("t_auc_seller");
        long buyerSn = insertUser("t_auc_buyer");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000), "TRDC0010");
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));
        creditAvailable(buyerSn, 50000);

        auctionService.buyNow(aucSn, buyerSn, new AuctionBuyNowRequest());

        assertThat(materialTradeCount(prdSn)).isEqualTo(1);
        assertThat(chatRoomCount(prdSn)).isZero();
        assertThat(deliverySnapshotCount(prdSn)).isZero();
    }

    @Test
    @DisplayName("최고입찰이 있는 만료 경매는 종료되고 홀딩이 보관금으로 전환된다")
    void finalizeExpiredAuctionEndsWithWinningBid() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(12000),
                databaseNow().minusMinutes(1),
                0);
        long bidSn = insertBid(aucSn, bidderSn, BigDecimal.valueOf(12000), "BIDC0001");
        creditAvailable(bidderSn, 50000);
        pointService.hold(bidderSn, 12000, RefType.BID, bidSn, "낙찰 테스트 홀딩");

        boolean finalized = auctionService.finalizeExpiredAuction(aucSn);

        assertThat(finalized).isTrue();
        assertThat(auctionStatus(aucSn)).isEqualTo("AUCC0003");
        assertThat(pointService.getBalance(bidderSn).getHoldAmt()).isZero();
        assertThat(escrowLedgerCount(bidderSn, bidSn)).isEqualTo(1);
        assertThat(materialTradeCount(prdSn)).isEqualTo(1);
        assertThat(deliverySnapshotCount(prdSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("입찰이 없는 만료 경매는 유찰된다")
    void finalizeExpiredAuctionFailsWithoutBid() {
        long sellerSn = insertUser("t_auc_seller");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(10000),
                databaseNow().minusMinutes(1),
                0);

        boolean finalized = auctionService.finalizeExpiredAuction(aucSn);

        assertThat(finalized).isTrue();
        assertThat(auctionStatus(aucSn)).isEqualTo("AUCC0004");
        assertThat(materialTradeCount(prdSn)).isZero();
    }

    @Test
    @DisplayName("낙찰자의 홀딩이 없으면 경매 종료 상태를 확정하지 않는다")
    void finalizeExpiredAuctionRequiresWinningBidHold() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(12000),
                databaseNow().minusMinutes(1),
                0);
        insertBid(aucSn, bidderSn, BigDecimal.valueOf(12000), "BIDC0001");

        assertThatThrownBy(() -> auctionService.finalizeExpiredAuction(aucSn))
                .isInstanceOf(PointException.class);
        assertThat(auctionStatus(aucSn)).isEqualTo("AUCC0002");
    }

    @Test
    @DisplayName("이미 확정된 만료 경매는 다시 처리하지 않는다")
    void finalizeExpiredAuctionOnlyOnce() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(12000),
                databaseNow().minusMinutes(1),
                0);
        long bidSn = insertBid(aucSn, bidderSn, BigDecimal.valueOf(12000), "BIDC0001");
        creditAvailable(bidderSn, 50000);
        pointService.hold(bidderSn, 12000, RefType.BID, bidSn, "낙찰 테스트 홀딩");

        assertThat(auctionService.finalizeExpiredAuction(aucSn)).isTrue();
        assertThat(auctionService.finalizeExpiredAuction(aucSn)).isFalse();
        assertThat(escrowLedgerCount(bidderSn, bidSn)).isEqualTo(1);
        assertThat(materialTradeCount(prdSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("마감된 경매에는 입찰할 수 없다")
    void placeBidRejectsExpiredAuction() {
        long sellerSn = insertUser("t_auc_seller");
        long bidderSn = insertUser("t_auc_bidder");
        long prdSn = insertProduct(sellerSn);
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(10000),
                databaseNow().minusMinutes(1),
                0);
        creditAvailable(bidderSn, 50000);
        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(12000));

        assertThatThrownBy(() -> auctionService.placeBid(aucSn, bidderSn, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(bidCount(aucSn)).isZero();
    }

    @Test
    @DisplayName("마감된 경매에는 즉시구매할 수 없다")
    void buyNowRejectsExpiredAuction() {
        long sellerSn = insertUser("t_auc_seller");
        long buyerSn = insertUser("t_auc_buyer");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(10000),
                databaseNow().minusMinutes(1),
                0);
        creditAvailable(buyerSn, 50000);

        assertThatThrownBy(() -> auctionService.buyNow(aucSn, buyerSn, new AuctionBuyNowRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(bidCount(aucSn)).isZero();
    }

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (
                    USR_LOGIN_ID,
                    USR_PSWD_HASH,
                    USR_NM,
                    USR_EML,
                    USR_STATUS_CD,
                    USR_ROLE_CD,
                    USR_ADDR,
                    USR_DADDR,
                    USR_ZIP
                )
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER', '테스트 주소', '101호', '12345')
                """, loginId, prefix, loginId + "@test.local");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertProduct(long sellerSn) {
        return insertProduct(sellerSn, null);
    }

    private long insertProduct(long sellerSn, BigDecimal instantBuyAmount) {
        return insertProduct(sellerSn, instantBuyAmount, "TRDC0009");
    }

    private long insertProduct(long sellerSn, BigDecimal instantBuyAmount, String tradeMethodCode) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '테스트 경매 상품', 'PRDC0002', 10000, ?, ?)
                """, sellerSn, instantBuyAmount, tradeMethodCode);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAuction(long prdSn, BigDecimal currentAmount) {
        return insertAuction(prdSn, currentAmount, LocalDateTime.now().plusHours(1), 0);
    }

    private long insertAuction(long prdSn, BigDecimal currentAmount, String statusCode) {
        return insertAuction(prdSn, currentAmount, LocalDateTime.now().plusHours(1), 0, statusCode);
    }

    private long insertAuction(long prdSn, BigDecimal currentAmount, LocalDateTime endDateTime, int extensionCount) {
        return insertAuction(prdSn, currentAmount, endDateTime, extensionCount, "AUCC0002");
    }

    private long insertAuction(
            long prdSn,
            BigDecimal currentAmount,
            LocalDateTime endDateTime,
            int extensionCount,
            String statusCode) {
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
                VALUES (?, ?, ?, 1000, ?, ?, ?)
                """,
                prdSn,
                statusCode,
                currentAmount,
                LocalDateTime.now().minusHours(1),
                endDateTime,
                extensionCount);
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

    private int productViewCount(long prdSn) {
        return jdbc.queryForObject(
                "SELECT PRD_VIEW_CNT FROM PRODUCT WHERE PRD_SN = ?",
                Integer.class,
                prdSn);
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

    private int escrowLedgerCount(long usrSn, long bidSn) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM POINT_LEDGER
                WHERE USR_SN = ?
                  AND PT_LDG_TYPE_CD = 'PTLC0007'
                  AND PT_LDG_REF_TYPE_CD = ?
                  AND PT_LDG_REF_SN = ?
                """, Integer.class, usrSn, RefType.BID.getCode(), bidSn);
    }

    private String auctionStatus(long aucSn) {
        return jdbc.queryForObject(
                "SELECT AUC_STATUS_CD FROM AUCTION WHERE AUC_SN = ?",
                String.class,
                aucSn);
    }

    private LocalDateTime databaseNow() {
        return jdbc.queryForObject("SELECT NOW()", LocalDateTime.class);
    }

    private boolean isTransactionRollbackOnly() {
        ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        return holder != null && holder.isRollbackOnly();
    }

    private int auctionRemainingSeconds(long aucSn) {
        return jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, NOW(), AUC_END_DT) FROM AUCTION WHERE AUC_SN = ?",
                Integer.class,
                aucSn);
    }

    private int auctionExtensionCount(long aucSn) {
        return jdbc.queryForObject("SELECT AUC_EXT_CNT FROM AUCTION WHERE AUC_SN = ?", Integer.class, aucSn);
    }

    private int bidCount(long aucSn) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM BID WHERE AUC_SN = ?", Integer.class, aucSn);
    }

    private int materialTradeCount(long prdSn) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM TRADE
                WHERE PRD_SN = ?
                  AND TRD_TYPE_CD = 'TRDC0001'
                """, Integer.class, prdSn);
    }

    private int deliverySnapshotCount(long prdSn) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM TRADE_DELIVERY d
                JOIN TRADE t ON t.TRD_SN = d.TRD_SN
                WHERE t.PRD_SN = ?
                """, Integer.class, prdSn);
    }

    private int chatRoomCount(long prdSn) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM CHAT_ROOM c
                JOIN TRADE t ON t.TRD_SN = c.TRD_SN
                WHERE t.PRD_SN = ?
                """, Integer.class, prdSn);
    }
}
