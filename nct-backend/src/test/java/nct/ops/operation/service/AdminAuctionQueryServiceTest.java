package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.auction.dto.AuctionDetailResponse;
import nct.auction.service.AuctionService;
import nct.product.dto.ProductResponse;
import nct.product.service.ProductService;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.service.TradeService;

/** 담당자 7 · F-OPS-003: 상품·경매·거래 상태를 하나의 관리자 응답으로 조합하는지 검증합니다. */
class AdminAuctionQueryServiceTest {

    private AuctionService auctionService;
    private ProductService productService;
    private TradeService tradeService;
    private AdminAuctionQueryService service;

    @BeforeEach
    void setUp() {
        auctionService = mock(AuctionService.class);
        productService = mock(ProductService.class);
        tradeService = mock(TradeService.class);
        service = new AdminAuctionQueryService(auctionService, productService, tradeService);
    }

    @Test
    void returnsProductAuctionBidAndTradeStatus() {
        AuctionDetailResponse auction = new AuctionDetailResponse();
        auction.setAuctionId(81L);
        auction.setProductId(31L);
        ProductResponse product = ProductResponse.builder().prdSn(31L).prdStatusCd("PRDC0002").build();
        SellerTradeStatusItem trade = new SellerTradeStatusItem();
        trade.setPrdSn(31L);
        trade.setTradeSn(51L);
        trade.setTradeStatusCd("TRDC0003");

        when(auctionService.findAuctionDetail(81L)).thenReturn(auction);
        when(productService.getProduct(31L)).thenReturn(product);
        when(tradeService.getTradeStatusesByProducts(List.of(31L))).thenReturn(List.of(trade));

        var response = service.getAuctionOverview(81L);

        assertThat(response.getProduct()).isSameAs(product);
        assertThat(response.getAuction()).isSameAs(auction);
        assertThat(response.getTradeSn()).isEqualTo(51L);
        assertThat(response.getTradeStatusCode()).isEqualTo("TRDC0003");
    }
}
