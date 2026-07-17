package nct.auction.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.dto.MyBidHistoryItem;
import nct.auction.mapper.BidMapper;
import nct.auction.service.BidService;
import nct.global.exception.GlobalExceptionHandler;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;

/**
 * [F-AUC-022 "실제 API 호출" 검증 - GET /api/bids/me]
 * - F-AUC-013/014/018/019 기술 소유권이 담당자5로 이관되면서(CHG-018) BidService 의존성이
 *   BidMapper 하나로 줄었다 - AuctionMapper/ProductQueryPort/PointLedgerPort/TradeCreationPort
 *   준비가 더 이상 필요 없다.
 */
@ExtendWith(MockitoExtension.class)
class MyBidHistoryControllerApiTest {

    @Mock private BidMapper bidMapper;

    private MockMvc mockMvc;

    private static final Long BIDDER_USR_SN = 1L;

    @BeforeEach
    void setUp() {
        BidService bidService = new BidService(bidMapper);
        MyBidHistoryController controller = new MyBidHistoryController(bidService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long usrSn) {
        AuthMember authMember = AuthMember.builder()
                .id(usrSn).email("bidder" + usrSn + "@test.com").role("ROLE_USER").build();
        CustomUserDetails principal = new CustomUserDetails(authMember);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void 로그인한_사용자의_입찰_내역이_배열로_응답된다() throws Exception {
        // given
        when(bidMapper.findMyBidHistory(BIDDER_USR_SN)).thenReturn(List.of(
                MyBidHistoryItem.builder()
                        .bidSn(1L).aucSn(100L).bidAmt(11000L)
                        .bidStatusCd(BidStatusCode.ACTIVE)
                        .auctionStatusCd(AuctionStatusCode.IN_PROGRESS)
                        .build(),
                MyBidHistoryItem.builder()
                        .bidSn(2L).aucSn(101L).bidAmt(9000L)
                        .bidStatusCd(BidStatusCode.OUTBID)
                        .auctionStatusCd(AuctionStatusCode.IN_PROGRESS)
                        .build()));
        loginAs(BIDDER_USR_SN);

        // when & then
        mockMvc.perform(get("/api/bids/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].bidSn").value(1))
                .andExpect(jsonPath("$.data[0].bidStatusCd").value(BidStatusCode.ACTIVE))
                .andExpect(jsonPath("$.data[1].bidStatusCd").value(BidStatusCode.OUTBID));
    }

    @Test
    void 입찰_내역이_없으면_빈_배열을_응답한다() throws Exception {
        when(bidMapper.findMyBidHistory(BIDDER_USR_SN)).thenReturn(List.of());
        loginAs(BIDDER_USR_SN);

        mockMvc.perform(get("/api/bids/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
