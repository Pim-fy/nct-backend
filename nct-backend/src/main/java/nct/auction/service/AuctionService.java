package nct.auction.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.dto.AuctionListItem;
import nct.auction.dto.AuctionListRequest;
import nct.auction.dto.AuctionListResponse;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.dto.AuctionStatusResponse;
import nct.auction.mapper.AuctionMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 60;

    private final AuctionMapper auctionMapper;

    public AuctionListResponse findAuctions(AuctionListRequest request) {
        normalize(request);

        long totalElements = auctionMapper.countAuctions(request);
        List<AuctionListItem> items = totalElements > 0
                ? auctionMapper.findAuctions(request)
                : List.of();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / request.getSize());

        return AuctionListResponse.builder()
                .items(items)
                .page(request.getPage())
                .size(request.getSize())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    @Transactional
    public AuctionDetailResponse findAuctionDetail(Long auctionId) {
        auctionMapper.incrementProductViewCount(auctionId);
        return loadAuctionDetail(auctionId);
    }

    @Transactional(readOnly = true)
    public AuctionStatusResponse getAuctionStatusByProduct(Long prdSn) {
        AuctionStatusResponse status = auctionMapper.findAuctionStatusByProduct(prdSn);
        if (status == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        return status;
    }

    private AuctionDetailResponse loadAuctionDetail(Long auctionId) {
        AuctionDetailResponse detail = auctionMapper.findAuctionDetail(auctionId);
        if (detail == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        detail.setImages(auctionMapper.findAuctionImages(detail.getProductId()));
        detail.setBids(auctionMapper.findAuctionBids(auctionId));
        return detail;
    }

    @Transactional
    public AuctionDetailResponse placeBid(Long auctionId, Long userId, AuctionBidRequest request) {
        AuctionBidTarget target = findBidTarget(auctionId);
        validateBidAvailable(target, userId);
        validateNotCurrentHighestBidder(target, userId);

        BigDecimal bidAmount = request == null ? null : request.getBidAmount();
        if (bidAmount == null || bidAmount.compareTo(minimumBidPrice(target)) < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        int updatedCount = auctionMapper.updateAuctionCurrentPrice(auctionId, bidAmount, userId.toString());
        if (updatedCount == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "현재가가 갱신되었습니다. 다시 확인 후 입찰해주세요.");
        }

        auctionMapper.updateCurrentHighestBids(auctionId);
        auctionMapper.insertBid(auctionId, userId, bidAmount, BidStatusCode.HIGHEST, userId.toString());

        return loadAuctionDetail(auctionId);
    }

    @Transactional
    public AuctionDetailResponse buyNow(Long auctionId, Long userId, AuctionBuyNowRequest request) {
        AuctionBidTarget target = findBidTarget(auctionId);
        validateBidAvailable(target, userId);

        BigDecimal instantBuyPrice = target.getInstantBuyPrice();
        if (instantBuyPrice == null || instantBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        auctionMapper.updateCurrentHighestBids(auctionId);
        auctionMapper.insertBid(auctionId, userId, instantBuyPrice, BidStatusCode.HIGHEST, userId.toString());
        auctionMapper.closeAuctionByInstantBuy(auctionId, instantBuyPrice, userId.toString());

        return loadAuctionDetail(auctionId);
    }

    private AuctionBidTarget findBidTarget(Long auctionId) {
        AuctionBidTarget target = auctionMapper.findAuctionBidTargetForUpdate(auctionId);
        if (target == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        return target;
    }

    private void validateBidAvailable(AuctionBidTarget target, Long userId) {
        if (!AuctionStatusCode.ACTIVE.equals(target.getAuctionStatusCode())) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        if (target.getEndDateTime() != null && target.getEndDateTime().isBefore(java.time.LocalDateTime.now())) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        if (target.getSellerId() != null && target.getSellerId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateNotCurrentHighestBidder(AuctionBidTarget target, Long userId) {
        if (target.getCurrentHighestBidderId() != null && target.getCurrentHighestBidderId().equals(userId)) {
            throw new CustomException(ErrorCode.CONFLICT, "현재 최고 입찰자입니다.");
        }
    }

    private BigDecimal minimumBidPrice(AuctionBidTarget target) {
        BigDecimal currentPrice = target.getCurrentPrice() == null ? BigDecimal.ZERO : target.getCurrentPrice();
        BigDecimal bidUnitPrice = target.getBidUnitPrice() == null ? BigDecimal.valueOf(1000) : target.getBidUnitPrice();
        return currentPrice.add(bidUnitPrice);
    }

    private void normalize(AuctionListRequest request) {
        if (request.getPage() < DEFAULT_PAGE) {
            request.setPage(DEFAULT_PAGE);
        }
        if (request.getSize() <= 0) {
            request.setSize(DEFAULT_SIZE);
        }
        if (request.getSize() > MAX_SIZE) {
            request.setSize(MAX_SIZE);
        }

        request.setKeyword(blankToNull(request.getKeyword()));
        request.setSort(blankToDefault(request.getSort(), "deadline"));
        request.setTradeMethod(blankToDefault(request.getTradeMethod(), "all"));
        request.setTradeMethodCode(resolveTradeMethodCode(request.getTradeMethod()));

        List<String> statuses = request.getStatus();
        boolean hasStatusFilter = statuses != null && !statuses.isEmpty();
        request.setHasStatusFilter(hasStatusFilter);
        request.setStatusReady(!hasStatusFilter || statuses.contains("ready"));
        request.setStatusActive(!hasStatusFilter || statuses.contains("active"));
        request.setStatusEndingSoon(hasStatusFilter && statuses.contains("endingSoon"));
    }

    private String resolveTradeMethodCode(String tradeMethod) {
        return switch (tradeMethod) {
            case "delivery" -> "TRDC0009";
            case "direct" -> "TRDC0010";
            default -> null;
        };
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
