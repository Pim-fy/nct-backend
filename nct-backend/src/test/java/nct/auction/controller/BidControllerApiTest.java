package nct.auction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.domain.Auction;
import nct.auction.domain.Bid;
import nct.auction.dto.ProductBidInfo;
import nct.auction.mapper.AuctionMapper;
import nct.auction.mapper.BidMapper;
import nct.auction.service.BidService;
import nct.auction.support.FakePointLedgerPort;
import nct.auction.support.FakeProductQueryPort;
import nct.global.exception.GlobalExceptionHandler;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;

/**
 * [F-AUC-013 "실제 API 호출" 검증 테스트]
 *
 * BidServiceTest 와의 차이:
 *   - BidServiceTest 는 BidService 자바 메서드를 직접 호출했다 (HTTP 계층 없음).
 *   - 이 테스트는 실제로 HTTP 요청을 만들어 DispatcherServlet 이 하는 일
 *     (JSON 파싱 -> @Valid 검증 -> 컨트롤러 실행 -> 예외 처리 -> JSON 응답 직렬화)
 *     을 전부 그대로 거치게 만든 뒤, 응답 JSON 을 검증한다.
 *
 * 왜 @SpringBootTest 로 전체 서버를 안 띄우는가:
 *   - 이 프로젝트의 application.properties 는 팀 공용 원격 MySQL(138.2.60.192)을 바라본다.
 *   - 전체 컨텍스트를 띄우면 MyBatis/DataSource 가 그 DB에 실제로 연결을 시도하고,
 *     테스트를 위해 그 DB에 없는 가짜 AUCTION/PRODUCT 행을 넣어야 하는 상황이 생긴다.
 *     이는 "팀 공유 자원에 흔적을 남기는" 부작용이 있어 피한다.
 *   - 대신 MockMvcBuilders.standaloneSetup() 으로 BidController 하나만 최소한의
 *     MVC 환경(JSON 컨버터, @Valid 검증기, 예외 핸들러, 인증 principal 리졸버) 위에 올린다.
 *     AuctionMapper 는 Mockito 로, ProductQueryPort/PointLedgerPort 는 Fake 로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class BidControllerApiTest {

    @Mock
    private AuctionMapper auctionMapper; // 실제 DB 접근이 필요한 부분만 Mockito 로 대체
    @Mock
    private BidMapper bidMapper;

    private final FakeProductQueryPort fakeProductQueryPort = new FakeProductQueryPort();
    private final FakePointLedgerPort fakePointLedgerPort = new FakePointLedgerPort();

    private MockMvc mockMvc;

    private static final Long AUC_SN = 100L;
    private static final Long PRD_SN = 200L;
    private static final Long BIDDER_USR_SN = 1L;
    private static final Long SELLER_USR_SN = 999L;

    @BeforeEach
    void setUp() {
        BidService bidService = new BidService(auctionMapper, bidMapper, fakeProductQueryPort, fakePointLedgerPort);
        BidController bidController = new BidController(bidService);

        // standaloneSetup: Spring 컨테이너 전체를 띄우지 않고 이 컨트롤러 하나만 MockMvc 위에 올린다.
        mockMvc = MockMvcBuilders.standaloneSetup(bidController)
                // 실전 코드와 동일한 전역 예외 처리기를 등록해야 CustomException -> 4xx JSON 변환이 재현된다.
                .setControllerAdvice(new GlobalExceptionHandler())
                // @AuthenticationPrincipal 파라미터를 해석하려면 이 리졸버를 명시적으로 등록해야 한다
                // (실제 서버에서는 Spring Security 자동설정이 해주지만, standalone 환경엔 없기 때문).
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    /** 매 테스트가 끝나면 로그인 정보를 지워서 다음 테스트에 영향이 가지 않도록 한다. */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 로그인한 사용자로 SecurityContext 를 채운다 (필터 체인 없이 principal 만 흉내낸다). */
    private void loginAs(Long usrSn) {
        AuthMember authMember = AuthMember.builder()
                .id(usrSn)
                .email("bidder" + usrSn + "@test.com")
                .role("ROLE_USER")
                .build();
        CustomUserDetails principal = new CustomUserDetails(authMember);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Auction healthyAuction() {
        return Auction.builder()
                .aucSn(AUC_SN)
                .prdSn(PRD_SN)
                .aucStatusCd(AuctionStatusCode.IN_PROGRESS)
                .aucCurAmt(10_000L)
                .aucBidUnitAmt(1_000L)
                .aucEndDt(LocalDateTime.now().plusHours(1))
                .build();
    }

    @Test
    void 유효한_입찰_요청은_200과_검증결과를_응답한다() throws Exception {
        // given: 실제 HTTP 요청이 도착하기 전에, 이 요청이 참조할 "가짜 세계"를 준비한다.
        when(auctionMapper.findAuctionForBid(eq(AUC_SN))).thenReturn(Optional.of(healthyAuction()));
        fakeProductQueryPort.register(PRD_SN,
                ProductBidInfo.builder().sellerUsrSn(SELLER_USR_SN).buyNowAmt(50_000L).build());
        fakePointLedgerPort.setBalance(BIDDER_USR_SN, 100_000L);
        loginAs(BIDDER_USR_SN);

        // when & then: 실제 POST 요청을 만들어 컨트롤러 -> 서비스 -> 응답까지 전부 거치게 한다.
        mockMvc.perform(post("/api/auctions/{aucSn}/bids/validate", AUC_SN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aucSn\": " + AUC_SN + ", \"bidAmt\": 11000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.aucSn").value(AUC_SN))
                .andExpect(jsonPath("$.data.bidderUsrSn").value(BIDDER_USR_SN))
                .andExpect(jsonPath("$.data.bidAmt").value(11000))
                .andExpect(jsonPath("$.data.nextMinAmt").value(11000)); // 10,000 + 1,000
    }

    @Test
    void 본인_상품에_입찰하면_403과_에러메시지를_응답한다() throws Exception {
        // given: 판매자 == 입찰자인 상황
        when(auctionMapper.findAuctionForBid(eq(AUC_SN))).thenReturn(Optional.of(healthyAuction()));
        fakeProductQueryPort.register(PRD_SN,
                ProductBidInfo.builder().sellerUsrSn(BIDDER_USR_SN).buyNowAmt(50_000L).build());
        loginAs(BIDDER_USR_SN);

        mockMvc.perform(post("/api/auctions/{aucSn}/bids/validate", AUC_SN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aucSn\": " + AUC_SN + ", \"bidAmt\": 11000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("본인이 등록한 상품에는 입찰할 수 없습니다."));
    }

    @Test
    void 최소_입찰_단위보다_낮으면_400과_에러메시지를_응답한다() throws Exception {
        when(auctionMapper.findAuctionForBid(eq(AUC_SN))).thenReturn(Optional.of(healthyAuction()));
        fakeProductQueryPort.register(PRD_SN,
                ProductBidInfo.builder().sellerUsrSn(SELLER_USR_SN).buyNowAmt(50_000L).build());
        loginAs(BIDDER_USR_SN);

        // 다음 최소 입찰가는 11,000 인데 10,500 을 보낸다.
        mockMvc.perform(post("/api/auctions/{aucSn}/bids/validate", AUC_SN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aucSn\": " + AUC_SN + ", \"bidAmt\": 10500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입찰 금액이 최소 입찰 단위보다 낮습니다."));
    }

    @Test
    void 입찰금액이_0이하면_컨트롤러_도달_전에_형식검증에서_400을_응답한다() throws Exception {
        // 이 케이스는 BidService 까지 가지도 않는다 - @NotNull/@Positive 형식 검증이 먼저 막는다.
        loginAs(BIDDER_USR_SN);

        mockMvc.perform(post("/api/auctions/{aucSn}/bids/validate", AUC_SN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aucSn\": " + AUC_SN + ", \"bidAmt\": -500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다."))
                .andExpect(jsonPath("$.data[0].field").value("bidAmt"));
    }

    /* ================================================================================
     *  F-AUC-014 "실제 API 호출" 검증 - POST /api/auctions/{aucSn}/bids (실행 엔드포인트)
     * ================================================================================ */

    @Test
    void 유효한_입찰_실행_요청은_200과_BID_생성결과를_응답한다() throws Exception {
        // given
        when(auctionMapper.findAuctionForUpdate(eq(AUC_SN))).thenReturn(Optional.of(healthyAuction()));
        when(bidMapper.findActiveBid(eq(AUC_SN), eq(BidStatusCode.ACTIVE))).thenReturn(Optional.empty());
        fakeProductQueryPort.register(PRD_SN,
                ProductBidInfo.builder().sellerUsrSn(SELLER_USR_SN).buyNowAmt(50_000L).build());
        fakePointLedgerPort.setBalance(BIDDER_USR_SN, 100_000L);
        loginAs(BIDDER_USR_SN);

        // insertBid 호출 시 실제 DB의 useGeneratedKeys 동작(PK 채워주기)을 흉내낸다.
        doAnswer(invocation -> {
            Bid savedBid = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBid, "bidSn", 777L);
            return null;
        }).when(bidMapper).insertBid(any(Bid.class));

        // when & then
        mockMvc.perform(post("/api/auctions/{aucSn}/bids", AUC_SN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aucSn\": " + AUC_SN + ", \"bidAmt\": 11000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.bidSn").value(777))
                .andExpect(jsonPath("$.data.aucSn").value(AUC_SN))
                .andExpect(jsonPath("$.data.bidderUsrSn").value(BIDDER_USR_SN))
                .andExpect(jsonPath("$.data.bidAmt").value(11000))
                .andExpect(jsonPath("$.data.previousHighestBidderUsrSn").doesNotExist()); // 첫 입찰 -> null -> JSON 에서 생략(NON_NULL)

        // 실제로 포인트 홀딩 계약이 정확한 인자로 호출되었는지까지 확인 (Fake 는 호출 기록을 남긴다).
        org.assertj.core.api.Assertions.assertThat(fakePointLedgerPort.holdCalls).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(fakePointLedgerPort.holdCalls.get(0).usrSn())
                .isEqualTo(BIDDER_USR_SN);
        org.assertj.core.api.Assertions.assertThat(fakePointLedgerPort.holdCalls.get(0).amount())
                .isEqualTo(11000L);
    }
}
