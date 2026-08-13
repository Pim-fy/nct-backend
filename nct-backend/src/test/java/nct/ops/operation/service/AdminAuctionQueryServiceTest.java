package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.auction.dto.AuctionDetailResponse;
import nct.auction.service.AuctionService;
import nct.member.port.AdminMemberIdentityReader;
import nct.ops.operation.dto.AdminAuctionListItemResponse;
import nct.ops.operation.dto.AdminAuctionListRequest;
import nct.ops.operation.mapper.AdminAuctionQueryMapper;
import nct.product.dto.ProductResponse;
import nct.product.service.ProductService;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.service.TradeService;

/** 담당자 7 · F-OPS-003: 상품·경매·거래 상태를 하나의 관리자 응답으로 조합하는지 검증합니다. */
class AdminAuctionQueryServiceTest {

    private AdminAuctionQueryMapper mapper;
    private AuctionService auctionService;
    private ProductService productService;
    private TradeService tradeService;
    private AdminMemberIdentityReader memberIdentityReader;
    private AdminAuctionQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminAuctionQueryMapper.class);
        auctionService = mock(AuctionService.class);
        productService = mock(ProductService.class);
        tradeService = mock(TradeService.class);
        memberIdentityReader = mock(AdminMemberIdentityReader.class);
        service = new AdminAuctionQueryService(
                mapper, auctionService, productService, tradeService, memberIdentityReader);
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

        when(auctionService.findAuctionDetailForAdmin(81L)).thenReturn(auction);
        when(productService.getProductForAdmin(31L)).thenReturn(product);
        when(tradeService.getTradeStatusesByProducts(List.of(31L))).thenReturn(List.of(trade));

        var response = service.getAuctionOverview(81L);

        assertThat(response.getProduct()).isSameAs(product);
        assertThat(response.getAuction()).isSameAs(auction);
        assertThat(response.getTradeSn()).isEqualTo(51L);
        assertThat(response.getTradeStatusCode()).isEqualTo("TRDC0003");
    }

    @Test
    void returnsPageWhenCancellationProcessorIsNotAssigned() {
        AdminAuctionListRequest request = new AdminAuctionListRequest();
        AdminAuctionListItemResponse item = new AdminAuctionListItemResponse();
        item.setSellerUserSn(7L);
        item.setCancelProcessorUserSn(null);

        when(mapper.count(request)).thenReturn(1L);
        when(mapper.findPage(request)).thenReturn(List.of(item));
        when(memberIdentityReader.findByUserSns(java.util.Set.of(7L))).thenReturn(Map.of());

        var response = service.getPage(request);

        assertThat(response.getItems()).containsExactly(item);
        assertThat(item.getCancelProcessorMember()).isNull();
    }
}
