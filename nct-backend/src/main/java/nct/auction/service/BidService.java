package nct.auction.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nct.auction.constant.BidStatusCode;
import nct.auction.domain.Auction;
import nct.auction.domain.Bid;
import nct.auction.dto.BidExecutionResult;
import nct.auction.dto.BidRequest;
import nct.auction.dto.BidValidationResult;
import nct.auction.dto.ProductBidInfo;
import nct.auction.mapper.AuctionMapper;
import nct.auction.mapper.BidMapper;
import nct.auction.port.PointLedgerPort;
import nct.auction.port.ProductQueryPort;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * [F-AUC-013 입찰 가능 여부 검증 + F-AUC-014 입찰 실행]
 * - 로직정의서 기준 6개 규칙을 "가장 저렴한 검증부터" 순서대로 통과시킨다.
 *   1. 자기 상품 입찰 차단        (이미 조회한 데이터 안에서 끝남 - DB 추가 조회 없음)
 *   2. 경매 상태 검증              (조회한 Auction 안에서 끝남)
 *   3. 종료시각 검증                (조회한 Auction 안에서 끝남)
 *   4. 즉시구매가 미만 검증        (담당자2 계약 호출 결과 재사용)
 *   5. 최소 입찰 단위 검증          (조회한 Auction 안에서 끝남)
 *   6. 보유 포인트 검증            (담당자6 계약 호출 - 가장 비용이 큰 검증이라 마지막에 배치)
 *
 * - 순서를 이렇게 잡은 이유: 어차피 하나라도 실패하면 즉시 예외를 던지고 끝나므로(fail-fast),
 *   외부 계약 호출(4, 6번)을 뒤로 미루면 앞단 규칙에서 걸러지는 요청은 불필요한 호출을 하지 않는다.
 *
 * - F-AUC-014(executeBid)는 위 6개 규칙을 "그대로 재사용"하되, 013(validateBid)의 읽기 전용 조회
 *   대신 FOR UPDATE 로 잠근 스냅샷을 기준으로 다시 검증한다. 이렇게 검증을 두 번 하는 이유는
 *   013 -> 014 사이(예: 사용자가 확인 버튼을 누르기까지의 시간)에 다른 입찰이 먼저 성사되어
 *   경매 상태가 바뀔 수 있기 때문이다 (TOCTOU: Time-Of-Check to Time-Of-Use 문제).
 */
@Service
@RequiredArgsConstructor
public class BidService {

    private final AuctionMapper auctionMapper;
    private final BidMapper bidMapper;
    private final ProductQueryPort productQueryPort;   // 담당자2 계약 (미구현 시 Fake Bean 으로 대체 가능)
    private final PointLedgerPort pointLedgerPort;     // 담당자6 계약 (미구현 시 Fake Bean 으로 대체 가능)

    /**
     * @param bidderUsrSn 로그인한 입찰자 회원번호 (컨트롤러에서 CustomUserDetails 로부터 꺼내 전달)
     * @param request     입찰 요청 (경매번호 + 입찰금액)
     * @return 모든 규칙을 통과했을 때만 반환되는 결과 (F-AUC-014 가 이 값을 그대로 소비)
     */
    public BidValidationResult validateBid(Long bidderUsrSn, BidRequest request) {

        // 0. 경매 조회 - 이후 모든 규칙이 이 한 번의 조회 결과를 재사용한다.
        Auction auction = auctionMapper.findAuctionForBid(request.getAucSn())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // 담당자2 계약 호출 - PRODUCT 테이블에 직접 접근하지 않는다 (7.1절 경계 규칙).
        ProductBidInfo product = productQueryPort.getBidInfo(auction.getPrdSn());

        long nextMinAmt = assertBiddable(auction, product, bidderUsrSn, request.getBidAmt());

        // 모든 규칙 통과 - 014(입찰 실행)가 소비할 결과를 만들어 반환한다.
        return BidValidationResult.builder()
                .aucSn(auction.getAucSn())
                .bidderUsrSn(bidderUsrSn)
                .bidAmt(request.getBidAmt())
                .nextMinAmt(nextMinAmt)
                .build();
    }

    /**
     * [F-AUC-014 입찰 실행]
     * 처리 순서:
     *   1) 경매 행을 FOR UPDATE 로 잠근다 (동시 입찰 직렬화).
     *   2) 잠근 스냅샷 기준으로 013 검증 규칙을 다시 통과시킨다.
     *   3) 기존 최고 입찰(ACTIVE)이 있으면 OUTBID 로 전환 + 그 사람의 홀딩 포인트를 반환한다(F-AUC-016).
     *   4) 새 입찰을 ACTIVE 상태로 기록한다.
     *   5) 새 입찰자의 포인트를 홀딩한다(F-AUC-015).
     *   6) 경매의 현재금액(AUC_CUR_AMT)을 갱신한다.
     *
     * @Transactional 이 필요한 이유: 4~6번 중 하나라도 실패하면(예: 포인트 홀딩 중 예외) 전부
     * 롤백되어야 "BID는 기록됐는데 포인트는 홀딩 안 됨" 같은 불일치가 생기지 않는다.
     * (CustomException 은 BaseException(RuntimeException) 기반이라 별도 rollbackFor 지정이 필요 없다.)
     */
    @Transactional
    public BidExecutionResult executeBid(Long bidderUsrSn, BidRequest request) {

        // 1. 쓰기 잠금 조회 - 이 트랜잭션이 끝날 때까지 이 경매 행에 대한 다른 트랜잭션의
        //    FOR UPDATE 조회는 대기한다 (MySQL InnoDB 기본 격리수준 REPEATABLE READ 기준).
        Auction auction = auctionMapper.findAuctionForUpdate(request.getAucSn())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        ProductBidInfo product = productQueryPort.getBidInfo(auction.getPrdSn());

        // 2. 013과 동일한 규칙을 "잠긴 최신 데이터" 기준으로 재검증한다.
        assertBiddable(auction, product, bidderUsrSn, request.getBidAmt());

        // 3. 기존 최고 입찰 처리 - 없으면(Optional.empty) 이 경매의 첫 입찰이라는 뜻이다.
        Optional<Bid> previousActiveBid = bidMapper.findActiveBid(auction.getAucSn(), BidStatusCode.ACTIVE);
        Long previousBidderUsrSn = null;
        if (previousActiveBid.isPresent()) {
            Bid previous = previousActiveBid.get();
            bidMapper.updateBidStatus(previous.getBidSn(), BidStatusCode.OUTBID);
            // F-AUC-016: 새 최고 입찰이 확정된 즉시 이전 최고 입찰자의 홀딩을 반환한다.
            pointLedgerPort.releaseHold(previous.getUsrSn(), previous.getBidAmt(), previous.getBidSn());
            previousBidderUsrSn = previous.getUsrSn();
        }

        // 4. 새 입찰 기록 (ACTIVE) - insertBid 실행 후 newBid.getBidSn() 에 생성된 PK 가 채워진다.
        Bid newBid = Bid.builder()
                .aucSn(auction.getAucSn())
                .usrSn(bidderUsrSn)
                .bidAmt(request.getBidAmt())
                .bidStatusCd(BidStatusCode.ACTIVE)
                .build();
        bidMapper.insertBid(newBid);

        // 5. F-AUC-015: 새 입찰자의 포인트를 홀딩한다.
        pointLedgerPort.holdForBid(bidderUsrSn, request.getBidAmt(), newBid.getBidSn());

        // 6. 경매 현재금액 갱신 - 다음 입찰자의 최소 입찰가 계산 기준이 된다.
        auctionMapper.updateCurrentAmount(auction.getAucSn(), request.getBidAmt());

        return BidExecutionResult.builder()
                .bidSn(newBid.getBidSn())
                .aucSn(auction.getAucSn())
                .bidderUsrSn(bidderUsrSn)
                .bidAmt(request.getBidAmt())
                .previousHighestBidderUsrSn(previousBidderUsrSn)
                .build();
    }

    /**
     * F-AUC-013의 6개 규칙을 한 곳에서 실행한다. validateBid(013)와 executeBid(014) 양쪽에서
     * 재사용해서, "검증 규칙이 두 군데에 따로 존재해 서로 어긋나는" 상황을 막는다.
     * @return 검증 시점 기준 다음 최소 입찰가 (호출한 쪽에서 필요하면 사용)
     */
    private long assertBiddable(Auction auction, ProductBidInfo product, Long bidderUsrSn, long bidAmt) {
        // 1. 자기 상품 입찰 차단
        validateNotSelfBid(bidderUsrSn, product);

        // 2. 경매 상태 검증
        validateAuctionInProgress(auction);

        // 3. 종료시각 검증
        validateBeforeEndTime(auction);

        // 4. 즉시구매가 미만 검증
        validateBelowBuyNowPrice(bidAmt, product);

        // 5. 최소 입찰 단위 검증
        long nextMinAmt = auction.nextMinBidAmount();
        validateMinBidUnit(bidAmt, nextMinAmt);

        // 6. 보유 포인트 검증 - 담당자6 계약 호출, POINT_LEDGER 직접 조회 금지
        validateAvailablePoint(bidderUsrSn, bidAmt);

        return nextMinAmt;
    }

    /* ================================================================
     * 아래는 규칙 하나당 메서드 하나씩 - 이렇게 쪼개두면
     *   1) 테스트 코드에서 각 규칙을 독립적으로 검증하기 쉽고
     *   2) validateBid() 본문만 읽어도 "무슨 규칙이 있는지" 순서대로 파악할 수 있다.
     * ================================================================ */

    /** 1. 자기 상품 입찰 차단 - 판매자 본인이 자기 상품에 입찰하는 것을 막는다. */
    private void validateNotSelfBid(Long bidderUsrSn, ProductBidInfo product) {
        if (product.getSellerUsrSn().equals(bidderUsrSn)) {
            throw new CustomException(ErrorCode.SELF_BID_NOT_ALLOWED);
        }
    }

    /** 2. 경매 상태 검증 - "진행중" 상태가 아니면 입찰 자체가 불가능하다. */
    private void validateAuctionInProgress(Auction auction) {
        if (!auction.isInProgress()) {
            throw new CustomException(ErrorCode.AUCTION_NOT_IN_PROGRESS);
        }
    }

    /** 3. 종료시각 검증 - 이미 종료 시각이 지났다면 상태값과 별개로 다시 한번 막는다. */
    private void validateBeforeEndTime(Auction auction) {
        if (!auction.isBeforeEndTime(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.AUCTION_ALREADY_ENDED);
        }
    }

    /**
     * 4. 즉시구매가 미만 검증
     * - 즉시구매가가 등록되지 않은 상품(buyNowAmt == null)은 이 규칙 자체를 건너뛴다.
     * - 즉시구매가 "이상"으로는 입찰할 수 없다 (같은 금액도 허용 안 함 -> 즉시구매를 이용해야 함).
     */
    private void validateBelowBuyNowPrice(long bidAmt, ProductBidInfo product) {
        Long buyNowAmt = product.getBuyNowAmt();
        if (buyNowAmt != null && bidAmt >= buyNowAmt) {
            throw new CustomException(ErrorCode.BID_AMOUNT_EXCEEDS_BUY_NOW);
        }
    }

    /** 5. 최소 입찰 단위 검증 - 현재금액 + 입찰단위금액 보다 낮은 입찰은 의미가 없다. */
    private void validateMinBidUnit(long bidAmt, long nextMinAmt) {
        if (bidAmt < nextMinAmt) {
            throw new CustomException(ErrorCode.BID_AMOUNT_TOO_LOW);
        }
    }

    /** 6. 보유 포인트 검증 - 담당자6 원장 계약을 호출해서 "가능/불가능"만 받는다. */
    private void validateAvailablePoint(Long bidderUsrSn, long bidAmt) {
        if (!pointLedgerPort.hasAvailableBalance(bidderUsrSn, bidAmt)) {
            throw new CustomException(ErrorCode.INSUFFICIENT_POINT_BALANCE);
        }
    }
}
