package nct.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.domain.Auction;
import nct.auction.domain.Bid;
import nct.auction.dto.BidExecutionResult;
import nct.auction.dto.BidRequest;
import nct.auction.dto.BidValidationResult;
import nct.auction.dto.BuyNowRequest;
import nct.auction.dto.BuyNowResult;
import nct.auction.dto.MyBidHistoryItem;
import nct.auction.dto.ProductBidInfo;
import nct.auction.mapper.AuctionMapper;
import nct.auction.mapper.BidMapper;
import nct.auction.port.PointLedgerPort;
import nct.auction.port.ProductQueryPort;
import nct.auction.port.TradeCreationPort;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/**
 * [F-AUC-013 입찰 가능 여부 검증 - 단위 테스트]
 *
 * 이 테스트는 "순수 단위 테스트"다. @SpringBootTest 로 전체 컨텍스트를 띄우지 않고
 * Mockito 로 AuctionMapper / ProductQueryPort / PointLedgerPort 를 가짜로 만들어
 * BidService 하나만 떼어내서 검증한다.
 *   - 장점: 실행이 빠르고, DB/다른 담당자 구현체가 없어도 지금 바로 돌릴 수 있다.
 *   - 이런 방식을 "Sociable하지 않은(고립된) 단위 테스트"라고 부른다.
 *
 * 테스트 이름은 "규칙_조건_결과" 형태의 한글 메서드명을 쓴다 (JUnit5는 한글 메서드명 허용).
 * BidService.validateBid() 안에서 규칙이 실행되는 순서(1~6)를 그대로 따라가며
 * "규칙 하나만 깨뜨리고 나머지는 정상"인 케이스를 하나씩 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    // BidService 가 의존하는 3개를 전부 가짜(Mock)로 대체한다.
    // - AuctionMapper: DB 조회를 흉내낸다 (실제 MySQL 연결 없이 "이런 값이 나왔다고 치자")
    // - ProductQueryPort / PointLedgerPort: 아직 담당자2·6의 실제 구현체가 없으므로
    //   "만약 이런 값을 준다면" 이라는 가정 하에 테스트한다.
    @Mock private AuctionMapper auctionMapper;
    @Mock private BidMapper bidMapper;
    @Mock private ProductQueryPort productQueryPort;
    @Mock private PointLedgerPort pointLedgerPort;
    @Mock private TradeCreationPort tradeCreationPort;

    private BidService bidService;

    // 테스트 전체에서 공통으로 쓰는 기준값
    private static final Long AUC_SN = 100L;
    private static final Long PRD_SN = 200L;
    private static final Long BIDDER_USR_SN = 1L;   // 입찰을 시도하는 사람
    private static final Long SELLER_USR_SN = 999L; // 상품 등록자(판매자) - 입찰자와 달라야 정상

    private static final long CUR_AMT = 10_000L;      // 경매 현재금액
    private static final long BID_UNIT_AMT = 1_000L;  // 입찰단위금액 -> 다음 최소입찰가 11,000
    private static final long BUY_NOW_AMT = 50_000L;  // 즉시구매금액
    private static final long VALID_BID_AMT = 11_000L; // 위 조건을 전부 만족하는 "정상" 입찰가

    @BeforeEach
    void setUp() {
        // @InjectMocks 를 쓰지 않고 직접 생성자로 조립했다.
        // 이유: 이 프로젝트는 @RequiredArgsConstructor(Lombok) 로 생성자를 만들기 때문에
        //       Mockito 의 필드 주입보다 "생성자 주입"이 실제 운영 코드와 더 가깝다.
        bidService = new BidService(auctionMapper, bidMapper, productQueryPort, pointLedgerPort, tradeCreationPort);
    }

    /** "정상" 경매 1건을 만드는 헬퍼 - 각 테스트는 여기서 필요한 필드 1개만 깨뜨려서 사용한다. */
    private Auction healthyAuction() {
        return Auction.builder()
                .aucSn(AUC_SN)
                .prdSn(PRD_SN)
                .aucStatusCd(AuctionStatusCode.IN_PROGRESS)
                .aucCurAmt(CUR_AMT)
                .aucBidUnitAmt(BID_UNIT_AMT)
                .aucEndDt(LocalDateTime.now().plusHours(1)) // 아직 한참 남음
                .build();
    }

    /** "정상" 상품 정보를 만드는 헬퍼 - 판매자는 입찰자와 다른 사람으로 기본 설정한다. */
    private ProductBidInfo healthyProduct() {
        return ProductBidInfo.builder()
                .sellerUsrSn(SELLER_USR_SN)
                .buyNowAmt(BUY_NOW_AMT)
                .build();
    }

    private BidRequest bidRequest(long bidAmt) {
        BidRequest request = new BidRequest();
        request.setAucSn(AUC_SN);
        request.setBidAmt(bidAmt);
        return request;
    }

    @Test
    void 본인이_등록한_상품에는_입찰할_수_없다() {
        // given: 판매자와 입찰자가 동일한 사람인 상황만 깨뜨린다.
        when(auctionMapper.findAuctionForBid(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(
                ProductBidInfo.builder()
                        .sellerUsrSn(BIDDER_USR_SN) // 판매자 == 입찰자
                        .buyNowAmt(BUY_NOW_AMT)
                        .build());

        // when & then: BidService 는 검증 실패를 CustomException 으로 던진다.
        assertThatThrownBy(() -> bidService.validateBid(BIDDER_USR_SN, bidRequest(VALID_BID_AMT)))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SELF_BID_NOT_ALLOWED);
    }

    @Test
    void 경매_상태가_진행중이_아니면_입찰할_수_없다() {
        // given: 상태만 "종료"로 깨뜨리고 나머지는 정상값 그대로 사용한다.
        Auction endedAuction = healthyAuction().toBuilder()
                .aucStatusCd(AuctionStatusCode.ENDED)
                .build();
        when(auctionMapper.findAuctionForBid(AUC_SN)).thenReturn(Optional.of(endedAuction));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct());

        assertThatThrownBy(() -> bidService.validateBid(BIDDER_USR_SN, bidRequest(VALID_BID_AMT)))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUCTION_NOT_IN_PROGRESS);
    }

    @Test
    void 종료시각이_이미_지났으면_입찰할_수_없다() {
        // given: 상태값은 "진행중"이라 해도, 종료시각이 과거라면 막아야 한다(이중 방어).
        Auction expiredAuction = healthyAuction().toBuilder()
                .aucEndDt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(auctionMapper.findAuctionForBid(AUC_SN)).thenReturn(Optional.of(expiredAuction));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct());

        assertThatThrownBy(() -> bidService.validateBid(BIDDER_USR_SN, bidRequest(VALID_BID_AMT)))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUCTION_ALREADY_ENDED);
    }

    @Test
    void 즉시구매가_이상_금액으로는_입찰할_수_없다() {
        // given: 입찰 금액을 즉시구매가와 "동일"하게 설정 (이상이면 실패해야 하므로 경계값 테스트)
        when(auctionMapper.findAuctionForBid(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct());

        assertThatThrownBy(() -> bidService.validateBid(BIDDER_USR_SN, bidRequest(BUY_NOW_AMT)))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BID_AMOUNT_EXCEEDS_BUY_NOW);
    }

    @Test
    void 최소_입찰_단위보다_낮은_금액은_입찰할_수_없다() {
        // given: 다음 최소 입찰가는 CUR_AMT + BID_UNIT_AMT = 11,000 인데, 그보다 낮은 10,500 을 입찰
        when(auctionMapper.findAuctionForBid(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct());

        long tooLowAmt = CUR_AMT + BID_UNIT_AMT - 500L; // 10,500
        assertThatThrownBy(() -> bidService.validateBid(BIDDER_USR_SN, bidRequest(tooLowAmt)))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BID_AMOUNT_TOO_LOW);
    }

    @Test
    void 보유_포인트가_부족하면_입찰할_수_없다() {
        // given: 다른 규칙은 모두 통과하지만, 포인트 잔액 확인 계약이 false 를 반환하는 상황
        when(auctionMapper.findAuctionForBid(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct());
        when(pointLedgerPort.hasAvailableBalance(BIDDER_USR_SN, VALID_BID_AMT)).thenReturn(false);

        assertThatThrownBy(() -> bidService.validateBid(BIDDER_USR_SN, bidRequest(VALID_BID_AMT)))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT_BALANCE);
    }

    @Test
    void 모든_조건을_만족하면_검증을_통과하고_결과를_반환한다() {
        // given: 6개 규칙을 전부 통과하는 "정상 시나리오"
        when(auctionMapper.findAuctionForBid(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct());
        when(pointLedgerPort.hasAvailableBalance(BIDDER_USR_SN, VALID_BID_AMT)).thenReturn(true);

        // when
        BidValidationResult result = bidService.validateBid(BIDDER_USR_SN, bidRequest(VALID_BID_AMT));

        // then: 014(입찰 실행)가 그대로 소비할 값들이 정확히 채워졌는지 확인한다.
        assertThat(result.getAucSn()).isEqualTo(AUC_SN);
        assertThat(result.getBidderUsrSn()).isEqualTo(BIDDER_USR_SN);
        assertThat(result.getBidAmt()).isEqualTo(VALID_BID_AMT);
        assertThat(result.getNextMinAmt()).isEqualTo(CUR_AMT + BID_UNIT_AMT); // 11,000
    }

    /* ================================================================================
     *  F-AUC-014 입찰 실행 테스트
     *  - executeBid() 는 013 검증(findAuctionForBid 아님, findAuctionForUpdate 사용)에 더해
     *    "BID 기록 + 이전 최고입찰 반환 + 포인트 홀딩 + 경매 현재금액 갱신"까지 한번에 처리한다.
     *  - BidMapper.insertBid() 는 실제로는 MyBatis useGeneratedKeys 가 bid.bidSn 필드를
     *    reflection 으로 채워준다. Mock 환경에서 이 동작을 재현하려면 doAnswer() 로
     *    "호출되면 인자로 받은 Bid 객체의 bidSn 필드를 직접 채워 넣어라"를 흉내내야 한다.
     *    ReflectionTestUtils.setField() 는 Bid 에 세터가 없어도 private 필드에 값을 넣어준다.
     * ================================================================================ */

    @Test
    void 첫_입찰이면_이전_입찰_반환_없이_새_입찰이_기록되고_포인트가_홀딩된다() {
        // given
        when(auctionMapper.findAuctionForUpdate(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct());
        when(pointLedgerPort.hasAvailableBalance(BIDDER_USR_SN, VALID_BID_AMT)).thenReturn(true);
        // 이 경매의 "현재 유효한 최고 입찰"이 아직 없다 - 첫 입찰 시나리오
        when(bidMapper.findActiveBid(AUC_SN, BidStatusCode.ACTIVE)).thenReturn(Optional.empty());

        Long generatedBidSn = 555L;
        doAnswer(invocation -> {
            Bid savedBid = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBid, "bidSn", generatedBidSn); // INSERT 후 PK 채워짐 흉내
            return null;
        }).when(bidMapper).insertBid(any(Bid.class));

        // when
        BidExecutionResult result = bidService.executeBid(BIDDER_USR_SN, bidRequest(VALID_BID_AMT));

        // then: 새 BID 가 ACTIVE 상태로 정확한 값과 함께 기록되었는지 확인
        ArgumentCaptor<Bid> bidCaptor = ArgumentCaptor.forClass(Bid.class);
        verify(bidMapper).insertBid(bidCaptor.capture());
        Bid savedBid = bidCaptor.getValue();
        assertThat(savedBid.getAucSn()).isEqualTo(AUC_SN);
        assertThat(savedBid.getUsrSn()).isEqualTo(BIDDER_USR_SN);
        assertThat(savedBid.getBidAmt()).isEqualTo(VALID_BID_AMT);
        assertThat(savedBid.getBidStatusCd()).isEqualTo(BidStatusCode.ACTIVE);

        // 첫 입찰이므로 "이전 최고 입찰 처리"는 전혀 일어나지 않아야 한다.
        verify(bidMapper, never()).updateBidStatus(any(), any());
        verify(pointLedgerPort, never()).releaseHold(any(), any(), any());

        // 새 입찰자의 포인트는 홀딩되어야 한다 (F-AUC-015).
        verify(pointLedgerPort).holdForBid(BIDDER_USR_SN, VALID_BID_AMT, generatedBidSn);

        // 경매 현재금액이 새 입찰가로 갱신되어야 한다.
        verify(auctionMapper).updateCurrentAmount(AUC_SN, VALID_BID_AMT);

        // 컨트롤러/클라이언트가 받을 응답도 올바른지 확인
        assertThat(result.getBidSn()).isEqualTo(generatedBidSn);
        assertThat(result.getPreviousHighestBidderUsrSn()).isNull();
    }

    @Test
    void 기존_최고_입찰이_있으면_OUTBID로_전환하고_포인트를_반환한다() {
        // given: 이미 다른 사람이 CUR_AMT(10,000원)로 최고 입찰 중인 상황
        Long previousBidderUsrSn = 2L;
        Long previousBidSn = 300L;
        Bid previousActiveBid = Bid.builder()
                .bidSn(previousBidSn)
                .aucSn(AUC_SN)
                .usrSn(previousBidderUsrSn)
                .bidAmt(CUR_AMT)
                .bidStatusCd(BidStatusCode.ACTIVE)
                .build();

        when(auctionMapper.findAuctionForUpdate(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct());
        when(pointLedgerPort.hasAvailableBalance(BIDDER_USR_SN, VALID_BID_AMT)).thenReturn(true);
        when(bidMapper.findActiveBid(AUC_SN, BidStatusCode.ACTIVE)).thenReturn(Optional.of(previousActiveBid));

        Long generatedBidSn = 556L;
        doAnswer(invocation -> {
            Bid savedBid = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBid, "bidSn", generatedBidSn);
            return null;
        }).when(bidMapper).insertBid(any(Bid.class));

        // when
        BidExecutionResult result = bidService.executeBid(BIDDER_USR_SN, bidRequest(VALID_BID_AMT));

        // then: 이전 최고 입찰은 OUTBID 로 바뀌고, 그 사람의 홀딩은 즉시 반환되어야 한다 (F-AUC-016).
        verify(bidMapper).updateBidStatus(previousBidSn, BidStatusCode.OUTBID);
        verify(pointLedgerPort).releaseHold(previousBidderUsrSn, CUR_AMT, previousBidSn);

        // 새 입찰자의 포인트는 별도로 홀딩된다 (F-AUC-015) - 반환과 홀딩이 서로 다른 사람 앞으로 일어나야 한다.
        verify(pointLedgerPort).holdForBid(BIDDER_USR_SN, VALID_BID_AMT, generatedBidSn);

        assertThat(result.getPreviousHighestBidderUsrSn()).isEqualTo(previousBidderUsrSn);
    }

    @Test
    void 검증에_실패하면_BID_기록도_포인트_홀딩도_일어나지_않는다() {
        // given: 자기 상품 입찰(검증 규칙 1번)로 실패를 유도 - 013의 검증이 014에도 그대로 적용됨을 확인
        when(auctionMapper.findAuctionForUpdate(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(
                ProductBidInfo.builder().sellerUsrSn(BIDDER_USR_SN).buyNowAmt(BUY_NOW_AMT).build());

        // when & then
        assertThatThrownBy(() -> bidService.executeBid(BIDDER_USR_SN, bidRequest(VALID_BID_AMT)))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SELF_BID_NOT_ALLOWED);

        // 검증에서 막혔으므로 쓰기 작업이 전혀 일어나지 않아야 한다 - "절반만 실행된 부작용"을 막는 게 핵심이다.
        verify(bidMapper, never()).insertBid(any());
        verify(bidMapper, never()).updateBidStatus(any(), any());
        verify(pointLedgerPort, never()).holdForBid(any(), any(), any());
        verify(pointLedgerPort, never()).releaseHold(any(), any(), any());
        verify(auctionMapper, never()).updateCurrentAmount(any(), any());
    }

    /* ================================================================================
     *  F-AUC-018 즉시구매 실행 테스트
     *  - executeBuyNow() 도 executeBid() 와 마찬가지로 findAuctionForUpdate(잠금)를 쓴다.
     *    "같은 잠금 지점을 공유한다"는 사실 자체가 F-AUC-019(충돌 제어)의 구현이므로
     *    이 테스트 파일에서 그 동시성을 직접 재현하지는 않지만, 최소한 "잠긴 스냅샷을 쓴다"는
     *    것은 findAuctionForUpdate 를 호출하는지 확인하는 것으로 간접 검증한다.
     * ================================================================================ */

    private BuyNowRequest buyNowRequest() {
        BuyNowRequest request = new BuyNowRequest();
        request.setAucSn(AUC_SN);
        return request;
    }

    @Test
    void 즉시구매가가_없는_상품은_즉시구매할_수_없다() {
        // given: buyNowAmt 가 null 인 상품 - "즉시구매 미지원" 상품
        when(auctionMapper.findAuctionForUpdate(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(
                ProductBidInfo.builder().sellerUsrSn(SELLER_USR_SN).buyNowAmt(null).build());

        assertThatThrownBy(() -> bidService.executeBuyNow(BIDDER_USR_SN, buyNowRequest()))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUY_NOW_NOT_AVAILABLE);

        // 즉시구매가 자체가 없으므로 어떤 쓰기 작업도 일어나면 안 된다.
        verify(bidMapper, never()).insertBid(any());
        verify(tradeCreationPort, never()).createTradeForBuyNow(any(), any(), any(), any(), any());
    }

    @Test
    void 자기_상품은_즉시구매할_수_없다() {
        // given: 013/014와 동일한 자기입찰 차단 규칙이 즉시구매에도 그대로 적용되는지 확인
        when(auctionMapper.findAuctionForUpdate(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(
                ProductBidInfo.builder().sellerUsrSn(BIDDER_USR_SN).buyNowAmt(BUY_NOW_AMT).build());

        assertThatThrownBy(() -> bidService.executeBuyNow(BIDDER_USR_SN, buyNowRequest()))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SELF_BID_NOT_ALLOWED);
    }

    @Test
    void 이전_입찰이_없으면_즉시구매_성공시_거래가_생성되고_경매가_종료된다() {
        // given: 첫 입찰조차 없던 경매에 바로 즉시구매가 들어오는 상황
        when(auctionMapper.findAuctionForUpdate(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct()); // buyNowAmt = 50,000
        when(pointLedgerPort.hasAvailableBalance(BIDDER_USR_SN, BUY_NOW_AMT)).thenReturn(true);
        when(bidMapper.findActiveBid(AUC_SN, BidStatusCode.ACTIVE)).thenReturn(Optional.empty());
        when(tradeCreationPort.createTradeForBuyNow(AUC_SN, PRD_SN, SELLER_USR_SN, BIDDER_USR_SN, BUY_NOW_AMT))
                .thenReturn(9001L);

        Long generatedBidSn = 700L;
        doAnswer(invocation -> {
            Bid savedBid = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBid, "bidSn", generatedBidSn);
            return null;
        }).when(bidMapper).insertBid(any(Bid.class));

        // when
        BuyNowResult result = bidService.executeBuyNow(BIDDER_USR_SN, buyNowRequest());

        // then: 서버가 결정한 즉시구매가(BUY_NOW_AMT)로 구매자 포인트가 홀딩된다.
        verify(pointLedgerPort).holdForBid(BIDDER_USR_SN, BUY_NOW_AMT, generatedBidSn);
        // 이전 입찰이 없으므로 반환 로직은 호출되지 않는다.
        verify(pointLedgerPort, never()).releaseHold(any(), any(), any());

        // 경매는 종료 상태로 바뀌고, 현재금액은 즉시구매가로 갱신된다.
        verify(auctionMapper).updateStatus(AUC_SN, AuctionStatusCode.ENDED);
        verify(auctionMapper).updateCurrentAmount(AUC_SN, BUY_NOW_AMT);

        // 담당자4 계약이 정확한 판매자/구매자/금액으로 호출되어야 한다.
        verify(tradeCreationPort).createTradeForBuyNow(AUC_SN, PRD_SN, SELLER_USR_SN, BIDDER_USR_SN, BUY_NOW_AMT);

        assertThat(result.getFinalAmt()).isEqualTo(BUY_NOW_AMT);
        assertThat(result.getTradeSn()).isEqualTo(9001L);
        assertThat(result.getRefundedBidderUsrSn()).isNull();
    }

    @Test
    void 기존_최고_입찰이_있으면_즉시구매_성공시_그_입찰자에게_포인트를_반환한다() {
        // given: 이미 다른 사람이 입찰 중이던 경매에 즉시구매가 들어와 그 사람이 패배하는 상황
        Long previousBidderUsrSn = 2L;
        Long previousBidSn = 300L;
        Bid previousActiveBid = Bid.builder()
                .bidSn(previousBidSn)
                .aucSn(AUC_SN)
                .usrSn(previousBidderUsrSn)
                .bidAmt(CUR_AMT)
                .bidStatusCd(BidStatusCode.ACTIVE)
                .build();

        when(auctionMapper.findAuctionForUpdate(AUC_SN)).thenReturn(Optional.of(healthyAuction()));
        when(productQueryPort.getBidInfo(PRD_SN)).thenReturn(healthyProduct());
        when(pointLedgerPort.hasAvailableBalance(BIDDER_USR_SN, BUY_NOW_AMT)).thenReturn(true);
        when(bidMapper.findActiveBid(AUC_SN, BidStatusCode.ACTIVE)).thenReturn(Optional.of(previousActiveBid));
        when(tradeCreationPort.createTradeForBuyNow(any(), any(), any(), any(), any())).thenReturn(9002L);

        doAnswer(invocation -> {
            Bid savedBid = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBid, "bidSn", 701L);
            return null;
        }).when(bidMapper).insertBid(any(Bid.class));

        // when
        BuyNowResult result = bidService.executeBuyNow(BIDDER_USR_SN, buyNowRequest());

        // then: 기존 최고 입찰자는 OUTBID 전환 + 정확한 금액 환불을 받는다 (F-AUC-016 과 동일한 처리).
        verify(bidMapper).updateBidStatus(previousBidSn, BidStatusCode.OUTBID);
        verify(pointLedgerPort).releaseHold(previousBidderUsrSn, CUR_AMT, previousBidSn);

        assertThat(result.getRefundedBidderUsrSn()).isEqualTo(previousBidderUsrSn);
    }

    /* ================================================================================
     *  F-AUC-022 내 입찰 내역 조회 테스트
     *  - 순수 조회라 검증할 게 많지 않다. 핵심은 "BidMapper 가 돌려준 걸 그대로 전달하는가"
     *    (서비스 계층이 괜히 가공하다가 데이터를 왜곡하지 않는지) 뿐이다.
     * ================================================================================ */

    @Test
    void 내_입찰_내역_조회는_Mapper_결과를_그대로_반환한다() {
        // given
        List<MyBidHistoryItem> expected = List.of(
                MyBidHistoryItem.builder()
                        .bidSn(1L).aucSn(AUC_SN).bidAmt(VALID_BID_AMT)
                        .bidStatusCd(BidStatusCode.ACTIVE)
                        .auctionStatusCd(AuctionStatusCode.IN_PROGRESS)
                        .build(),
                MyBidHistoryItem.builder()
                        .bidSn(2L).aucSn(AUC_SN).bidAmt(CUR_AMT)
                        .bidStatusCd(BidStatusCode.OUTBID)
                        .auctionStatusCd(AuctionStatusCode.IN_PROGRESS)
                        .build());
        when(bidMapper.findMyBidHistory(BIDDER_USR_SN)).thenReturn(expected);

        // when
        List<MyBidHistoryItem> result = bidService.getMyBidHistory(BIDDER_USR_SN);

        // then
        assertThat(result).isEqualTo(expected);
    }
}
