package nct.ops.operation.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.service.AuctionService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.product.dto.ProductResponse;
import nct.product.service.ProductService;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.service.TradeService;
import nct.ops.operation.dto.AdminAuctionListItemResponse;
import nct.ops.operation.dto.AdminAuctionListRequest;
import nct.ops.operation.dto.AdminAuctionPageResponse;
import nct.ops.operation.dto.AdminAuctionOverviewResponse;
import nct.ops.operation.mapper.AdminAuctionQueryMapper;

/** 담당자 7 · F-OPS-003: 관리자 상품·경매·입찰·거래 상태 조회 서비스입니다. */
@Service
@RequiredArgsConstructor
public class AdminAuctionQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final AdminAuctionQueryMapper mapper;
    private final AuctionService auctionService;
    private final ProductService productService;
    private final TradeService tradeService;

    @Transactional(readOnly = true)
    public AdminAuctionPageResponse getPage(AdminAuctionListRequest request) {
        AdminAuctionListRequest condition = request == null ? new AdminAuctionListRequest() : request;
        normalize(condition);
        long totalItems = mapper.count(condition);
        List<AdminAuctionListItemResponse> items = totalItems == 0 ? List.of() : mapper.findPage(condition);
        return AdminAuctionPageResponse.builder()
                .items(items).page(condition.getPage()).size(condition.getSize()).totalItems(totalItems)
                .totalPages((int) Math.ceil((double) totalItems / condition.getSize()))
                .build();
    }

    /** 기존 F-OPS-003 상세 조회 계약을 유지한다. */
    @Transactional(readOnly = true)
    public AdminAuctionOverviewResponse getAuctionOverview(Long auctionSn) {
        if (auctionSn == null || auctionSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AuctionDetailResponse auction = auctionService.findAuctionDetail(auctionSn);
        ProductResponse product = productService.getProduct(auction.getProductId());
        SellerTradeStatusItem trade = tradeService.getTradeStatusesByProducts(List.of(auction.getProductId()))
                .stream().findFirst().orElse(null);
        return AdminAuctionOverviewResponse.builder()
                .product(product).auction(auction)
                .tradeSn(trade == null ? null : trade.getTradeSn())
                .tradeStatusCode(trade == null ? null : trade.getTradeStatusCd())
                .build();
    }

    private void normalize(AdminAuctionListRequest request) {
        request.setPage(Math.max(1, request.getPage()));
        request.setSize(request.getSize() <= 0 ? DEFAULT_SIZE : Math.min(request.getSize(), MAX_SIZE));
        request.setKeyword(trimToNull(request.getKeyword()));
        request.setAuctionStatusCode(trimToNull(request.getAuctionStatusCode()));
        request.setTradeStatusCode(trimToNull(request.getTradeStatusCode()));
        if (request.getKeyword() != null && request.getKeyword().length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDate registeredFrom = request.getRegisteredFrom();
        LocalDate registeredTo = request.getRegisteredTo();
        if (registeredFrom != null && registeredTo != null && registeredFrom.isAfter(registeredTo)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
