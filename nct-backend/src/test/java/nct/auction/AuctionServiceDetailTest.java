package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import nct.auction.dto.AuctionDetailResponse;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionEventPublisher;
import nct.auction.service.AuctionService;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.point.service.PointService;
import nct.product.service.ProductService;
import nct.trade.service.TradeService;

@ExtendWith(MockitoExtension.class)
class AuctionServiceDetailTest {

    @Mock
    private AuctionMapper auctionMapper;

    @Mock
    private ProductFavoriteMapper productFavoriteMapper;

    @Mock
    private PointService pointService;

    @Mock
    private ObjectProvider<ProductService> productServiceProvider;

    @Mock
    private ProductService productService;

    @Mock
    private TradeService tradeService;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    @InjectMocks
    private AuctionService auctionService;

    @Test
    void findAuctionDetailDoesNotIncreaseProductViewCount() {
        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);

        when(auctionMapper.findProductIdByAuctionId(10L)).thenReturn(20L);
        when(productServiceProvider.getObject()).thenReturn(productService);
        when(auctionMapper.findAuctionDetail(10L)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());

        AuctionDetailResponse response = auctionService.findAuctionDetail(10L);

        assertThat(response).isSameAs(detail);
        verify(productService).getProduct(20L);
        verify(productService, never()).increaseViewCount(anyLong());
    }
}
