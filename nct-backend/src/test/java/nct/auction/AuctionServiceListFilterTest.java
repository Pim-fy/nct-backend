package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
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
import nct.global.exception.CustomException;
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
        assertThat(request.isStatusEnded()).isFalse();
        assertThat(request.isStatusEndingSoon()).isFalse();
        assertThat(request.getEndingSoonOnly()).isTrue();
    }

    @Test
    void includesEndedAuctionsWhenNoStatusFilterIsSelected() {
        when(auctionMapper.countAuctions(any())).thenReturn(0L);
        AuctionListRequest request = new AuctionListRequest();

        auctionService.findAuctions(request);

        assertThat(request.isStatusReady()).isTrue();
        assertThat(request.isStatusActive()).isTrue();
        assertThat(request.isStatusEnded()).isTrue();
    }

    @Test
    void supportsEndedAuctionStatusFilter() {
        when(auctionMapper.countAuctions(any())).thenReturn(0L);
        AuctionListRequest request = new AuctionListRequest();
        request.setStatus(List.of(AuctionStatusCode.ENDED));

        auctionService.findAuctions(request);

        assertThat(request.isStatusReady()).isFalse();
        assertThat(request.isStatusActive()).isFalse();
        assertThat(request.isStatusEnded()).isTrue();
        assertThat(request.isStatusEndingSoon()).isFalse();
    }

    @Test
    void sellerHistoryRequiresSellerId() {
        AuctionListRequest request = new AuctionListRequest();
        request.setIncludeHistory(true);

        assertThatThrownBy(() -> auctionService.findAuctions(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("판매자 번호");

        verifyNoInteractions(auctionMapper);
    }

    @Test
    void keepsSellerHistoryConditionForMapper() {
        when(auctionMapper.countAuctions(any())).thenReturn(0L);
        AuctionListRequest request = new AuctionListRequest();
        request.setSellerId(77L);
        request.setIncludeHistory(true);
        request.setPage(1);
        request.setSize(5);

        auctionService.findAuctions(request);

        assertThat(request.getSellerId()).isEqualTo(77L);
        assertThat(request.isIncludeHistory()).isTrue();
        assertThat(request.getSize()).isEqualTo(5);
    }

    @Test
    void keepsPopularitySortForMapper() {
        when(auctionMapper.countAuctions(any())).thenReturn(0L);
        AuctionListRequest request = new AuctionListRequest();
        request.setSort("popular");

        auctionService.findAuctions(request);

        assertThat(request.getSort()).isEqualTo("popular");
    }

    @Test
    void defaultsToLatestSortWhenSortIsBlank() {
        when(auctionMapper.countAuctions(any())).thenReturn(0L);
        AuctionListRequest request = new AuctionListRequest();
        request.setSort("  ");

        auctionService.findAuctions(request);

        assertThat(request.getSort()).isEqualTo("latest");
    }

    @Test
    void keepsFavoriteCountSortForMapper() {
        when(auctionMapper.countAuctions(any())).thenReturn(0L);
        AuctionListRequest request = new AuctionListRequest();
        request.setSort("favoritesDesc");

        auctionService.findAuctions(request);

        assertThat(request.getSort()).isEqualTo("favoritesDesc");
    }

    @Test
    void expandsTradeMethodToAvailableCapabilities() {
        when(auctionMapper.countAuctions(any())).thenReturn(0L);
        AuctionListRequest deliveryRequest = new AuctionListRequest();
        deliveryRequest.setTradeMethod("delivery");

        auctionService.findAuctions(deliveryRequest);

        assertThat(deliveryRequest.getTradeMethodCodes())
                .containsExactly("TRDC0009", "TRDC0015");

        AuctionListRequest directRequest = new AuctionListRequest();
        directRequest.setTradeMethod("direct");

        auctionService.findAuctions(directRequest);

        assertThat(directRequest.getTradeMethodCodes())
                .containsExactly("TRDC0010", "TRDC0015");
    }

    @Test
    void doesNotRestrictTradeMethodWhenAllIsSelected() {
        when(auctionMapper.countAuctions(any())).thenReturn(0L);
        AuctionListRequest request = new AuctionListRequest();
        request.setTradeMethod("all");

        auctionService.findAuctions(request);

        assertThat(request.getTradeMethodCodes()).isEmpty();
    }
}
