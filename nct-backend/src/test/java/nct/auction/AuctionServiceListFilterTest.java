package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionListRequest;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionEventPublisher;
import nct.auction.service.AuctionService;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.point.service.PointService;
import nct.product.service.ProductService;
import nct.trade.service.TradeService;

@ExtendWith(MockitoExtension.class)
class AuctionServiceListFilterTest {

    @Mock
    private AuctionMapper auctionMapper;

    @Mock
    private ProductFavoriteMapper productFavoriteMapper;

    @Mock
    private PointService pointService;

    @Mock
    private ObjectProvider<ProductService> productServiceProvider;

    @Mock
    private TradeService tradeService;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    @InjectMocks
    private AuctionService auctionService;

    @Test
    void keepsEndingSoonFilterIndependentFromAuctionStatusFilter() {
        when(auctionMapper.countAuctions(any())).thenReturn(0L);
        AuctionListRequest request = new AuctionListRequest();
        request.setStatus(List.of(AuctionStatusCode.READY));
        request.setEndingSoonOnly(true);

        auctionService.findAuctions(request);

        assertThat(request.isStatusReady()).isTrue();
        assertThat(request.isStatusActive()).isFalse();
        assertThat(request.isStatusEndingSoon()).isFalse();
        assertThat(request.getEndingSoonOnly()).isTrue();
    }
}
