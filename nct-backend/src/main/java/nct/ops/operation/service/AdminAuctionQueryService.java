package nct.ops.operation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.service.AuctionService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.operation.dto.AdminAuctionOverviewResponse;
import nct.product.dto.ProductResponse;
import nct.product.service.ProductService;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.service.TradeService;

/** 담당자 7 · F-OPS-003: 관리자에게 상품·경매·입찰·거래 상태를 함께 제공합니다. */
@Service
@RequiredArgsConstructor
public class AdminAuctionQueryService {

    private final AuctionService auctionService;
    private final ProductService productService;
    private final TradeService tradeService;

    @Transactional
    public AdminAuctionOverviewResponse getAuctionOverview(Long auctionSn) {
        if (auctionSn == null || auctionSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 사용자 상세 조회와 같은 계약을 사용하므로, 경매 상세 조회 시 상품 조회수도 함께 증가합니다.
        AuctionDetailResponse auction = auctionService.findAuctionDetail(auctionSn);
        ProductResponse product = productService.getProduct(auction.getProductId());
        SellerTradeStatusItem trade = tradeService
                .getTradeStatusesByProducts(List.of(auction.getProductId()))
                .stream()
                .findFirst()
                .orElse(null);

        return AdminAuctionOverviewResponse.builder()
                .product(product)
                .auction(auction)
                .tradeSn(trade == null ? null : trade.getTradeSn())
                .tradeStatusCode(trade == null ? null : trade.getTradeStatusCd())
                .build();
    }
}
