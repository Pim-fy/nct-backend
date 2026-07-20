package nct.auction.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.dto.AuctionBidCreateCommand;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.dto.AuctionListItem;
import nct.auction.dto.AuctionListRequest;
import nct.auction.dto.AuctionListResponse;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.dto.AuctionStatusResponse;
import nct.auction.mapper.AuctionMapper;
import nct.common.domain.RefType;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.point.exception.PointException;
import nct.point.service.PointService;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 60;
    private static final BigDecimal DEFAULT_BID_UNIT = BigDecimal.valueOf(1000);

    private final AuctionMapper auctionMapper;
    private final ProductFavoriteMapper productFavoriteMapper;
    private final PointService pointService;

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
        return loadAuctionDetail(auctionId, null);
    }

    @Transactional
    public AuctionDetailResponse findAuctionDetail(Long auctionId, Long userId) {
        auctionMapper.incrementProductViewCount(auctionId);
        return loadAuctionDetail(auctionId, userId);
    }

    @Transactional(readOnly = true)
    public AuctionStatusResponse getAuctionStatusByProduct(Long prdSn) {
        AuctionStatusResponse status = auctionMapper.findAuctionStatusByProduct(prdSn);
        if (status == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        return status;
    }

    @Transactional
    public void createAuctionForProduct(
            Long productId,
            BigDecimal startAmount,
            BigDecimal bidUnitAmount,
            LocalDateTime endDateTime,
            boolean openImmediately,
            Long actorUserId) {
        validateAuctionCreation(productId, startAmount, bidUnitAmount, endDateTime, actorUserId);

        String statusCode = openImmediately ? AuctionStatusCode.ACTIVE : AuctionStatusCode.READY;
        BigDecimal actualBidUnit = bidUnitAmount == null ? DEFAULT_BID_UNIT : bidUnitAmount;

        int inserted = auctionMapper.insertAuction(
                productId,
                statusCode,
                startAmount,
                actualBidUnit,
                endDateTime,
                actorUserId.toString());
        if (inserted == 0) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
    }

    private AuctionDetailResponse loadAuctionDetail(Long auctionId) {
        return loadAuctionDetail(auctionId, null);
    }

    private AuctionDetailResponse loadAuctionDetail(Long auctionId, Long userId) {
        AuctionDetailResponse detail = auctionMapper.findAuctionDetail(auctionId);
        if (detail == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        detail.setFavorite(userId != null
                && productFavoriteMapper.existsActive(detail.getProductId(), userId));
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
        validateBidBelowInstantBuyPrice(target, bidAmount);

        int updatedCount = auctionMapper.updateAuctionCurrentPrice(auctionId, bidAmount, userId.toString());
        if (updatedCount == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "현재가가 갱신되었습니다. 다시 확인 후 입찰해주세요.");
        }

        Long previousHighestBidId = target.getCurrentHighestBidId();
        Long previousHighestBidderId = target.getCurrentHighestBidderId();

        auctionMapper.updateCurrentHighestBids(auctionId);
        AuctionBidCreateCommand bid = insertHighestBid(auctionId, userId, bidAmount);
        pointService.hold(userId, toPointAmount(bidAmount), RefType.BID, bid.getBidId(), "입찰 포인트 홀딩");
        releasePreviousHighestBidHold(previousHighestBidderId, previousHighestBidId);
        auctionMapper.extendAuctionTime(auctionId, userId.toString());

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

        Long previousHighestBidId = target.getCurrentHighestBidId();
        Long previousHighestBidderId = target.getCurrentHighestBidderId();

        auctionMapper.updateCurrentHighestBids(auctionId);
        AuctionBidCreateCommand bid = insertHighestBid(auctionId, userId, instantBuyPrice);
        pointService.hold(userId, toPointAmount(instantBuyPrice), RefType.BID, bid.getBidId(), "즉시구매 포인트 홀딩");
        pointService.convertHoldToEscrow(userId, RefType.BID, bid.getBidId(), "즉시구매 보관금 전환");
        releasePreviousHighestBidHold(previousHighestBidderId, previousHighestBidId);
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

    private void validateBidBelowInstantBuyPrice(AuctionBidTarget target, BigDecimal bidAmount) {
        BigDecimal instantBuyPrice = target.getInstantBuyPrice();
        if (instantBuyPrice != null
                && instantBuyPrice.compareTo(BigDecimal.ZERO) > 0
                && bidAmount.compareTo(instantBuyPrice) >= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "입찰 금액은 즉시구매가보다 낮아야 합니다.");
        }
    }

    private AuctionBidCreateCommand insertHighestBid(Long auctionId, Long userId, BigDecimal bidAmount) {
        AuctionBidCreateCommand bid = new AuctionBidCreateCommand(
                auctionId,
                userId,
                bidAmount,
                BidStatusCode.HIGHEST,
                userId.toString());
        int inserted = auctionMapper.insertBid(bid);
        if (inserted == 0 || bid.getBidId() == null) {
            throw new CustomException(ErrorCode.CONFLICT, "입찰 등록에 실패했습니다.");
        }
        return bid;
    }

    private void releasePreviousHighestBidHold(Long previousHighestBidderId, Long previousHighestBidId) {
        if (previousHighestBidderId == null || previousHighestBidId == null) {
            return;
        }
        try {
            pointService.releaseHold(
                    previousHighestBidderId,
                    RefType.BID,
                    previousHighestBidId,
                    "상위 입찰 발생에 따른 기존 입찰 홀딩 반환");
        } catch (PointException ignored) {
            // 기존 테스트/마이그레이션 데이터처럼 포인트 홀딩 없이 존재하는 과거 입찰은 상태 변경만 유지한다.
        }
    }

    private long toPointAmount(BigDecimal amount) {
        try {
            return amount.longValueExact();
        } catch (ArithmeticException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "포인트 금액은 정수여야 합니다.");
        }
    }

    private void validateAuctionCreation(
            Long productId,
            BigDecimal startAmount,
            BigDecimal bidUnitAmount,
            LocalDateTime endDateTime,
            Long actorUserId) {
        if (productId == null || actorUserId == null || startAmount == null || endDateTime == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (startAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (bidUnitAmount != null && bidUnitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!endDateTime.isAfter(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
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
