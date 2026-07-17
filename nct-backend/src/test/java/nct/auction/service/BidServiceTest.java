package nct.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.dto.MyBidHistoryItem;
import nct.auction.mapper.BidMapper;

/**
 * [F-AUC-022 내 입찰 내역 조회 - 단위 테스트]
 * - F-AUC-013/014/018/019 관련 테스트는 해당 기능의 기술 소유권이 담당자5로 이관되면서
 *   함께 삭제했다 (09_기능단위_7인_업무분장 v10, CHG-018). 남은 F-AUC-022 테스트만 유지한다.
 */
@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock private BidMapper bidMapper;

    private BidService bidService;

    private static final Long AUC_SN = 100L;
    private static final Long BIDDER_USR_SN = 1L;
    private static final long VALID_BID_AMT = 11_000L;
    private static final long CUR_AMT = 10_000L;

    @BeforeEach
    void setUp() {
        bidService = new BidService(bidMapper);
    }

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
