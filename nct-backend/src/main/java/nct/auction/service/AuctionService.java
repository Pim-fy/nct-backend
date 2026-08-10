package nct.auction.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
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
import nct.auction.dto.AuctionProductUpdateItem;
import nct.auction.dto.AuctionRealtimeEvent;
import nct.auction.dto.AuctionStatusResponse;
import nct.auction.dto.AuctionStatusSummaryResponse;
import nct.auction.dto.AuctionTradeMethodChangeRequest;
import nct.auction.mapper.AuctionMapper;
import nct.common.domain.RefType;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.service.MemberService;
import nct.notification.service.NotificationService;
import nct.ops.reference.domain.CommonCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.point.domain.AuctionPolicy;
import nct.point.service.PointService;
import nct.product.dto.ProductCommentResponse;
import nct.product.service.ProductService;
import nct.review.dto.TrustScoreResponse;
import nct.review.service.ReviewService;
import nct.trade.domain.AuctionTradeSource;
import nct.trade.dto.AuctionTradeCreateCommand;
import nct.trade.dto.AuctionTradeCreateResult;
import nct.trade.service.TradeService;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 60;
    private static final int MAX_FINALIZATION_BATCH_SIZE = 500;
    private static final int MAX_NOTIFICATION_BATCH_SIZE = 500;
    private static final String SYSTEM_ACTOR = "SYSTEM";
    private static final String DELIVERY_TRADE_METHOD_CODE = "TRDC0009";
    private static final String OFFLINE_TRADE_METHOD_CODE = "TRDC0010";
    private static final String BOTH_TRADE_METHOD_CODE = "TRDC0020";
    private static final String BID_UNIT_GROUP_CODE = "AUCG02";

    private final AuctionMapper auctionMapper;
    private final ProductFavoriteMapper productFavoriteMapper;
    private final PointService pointService;
    private final MemberService memberService;
    private final ObjectProvider<ProductService> productServiceProvider;
    private final TradeService tradeService;
    private final AuctionEventPublisher auctionEventPublisher;
    private final NotificationService notificationService;
    private final ReviewService reviewService;
    private final ReferenceDataService referenceDataService;

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

    @Transactional(readOnly = true)
    public AuctionDetailResponse findAuctionDetail(Long auctionId) {
        return findAuctionDetailWithProductValidation(auctionId, null, true);
    }

    @Transactional(readOnly = true)
    public AuctionDetailResponse findAuctionDetail(Long auctionId, Long userId) {
        return findAuctionDetailWithProductValidation(auctionId, userId, true);
    }

    @Transactional(readOnly = true)
    public AuctionDetailResponse findAuctionDetail(
            Long auctionId,
            Long userId,
            boolean includeSupplemental) {
        return findAuctionDetailWithProductValidation(auctionId, userId, includeSupplemental);
    }

    @Transactional(readOnly = true)
    public AuctionStatusResponse getAuctionStatusByProduct(Long prdSn) {
        AuctionStatusResponse status = auctionMapper.findAuctionStatusByProduct(prdSn);
        if (status == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        return status;
    }

    @Transactional(readOnly = true)
    public List<AuctionStatusSummaryResponse> getAuctionStatusesByProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        List<Long> prdSns = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (prdSns.isEmpty()) {
            return List.of();
        }

        return auctionMapper.findAuctionStatusesByProducts(prdSns);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredActiveAuctionIds(int limit) {
        int batchSize = Math.max(1, Math.min(limit, MAX_FINALIZATION_BATCH_SIZE));
        return auctionMapper.findExpiredActiveAuctionIds(batchSize);
    }

    @Transactional(readOnly = true)
    public List<Long> findReadyAuctionIds(int limit) {
        int batchSize = Math.max(1, Math.min(limit, MAX_FINALIZATION_BATCH_SIZE));
        return auctionMapper.findReadyAuctionIds(batchSize);
    }

    @Transactional
    public boolean activateReadyAuction(Long auctionId) {
        if (auctionId == null || auctionId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (auctionMapper.activateReadyAuction(auctionId, SYSTEM_ACTOR) == 0) {
            return false;
        }
        publishAuctionChanged(auctionId, "AUCTION_ACTIVATED");
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> findClosingSoonActiveAuctionIds(int limit) {
        int batchSize = Math.max(1, Math.min(limit, MAX_NOTIFICATION_BATCH_SIZE));
        return auctionMapper.findClosingSoonActiveAuctionIds(batchSize);
    }

    @Transactional
    public int notifyClosingSoonAuction(Long auctionId) {
        List<Long> recipients = auctionMapper.findClosingSoonRecipientUserIds(auctionId);
        if (recipients == null || recipients.isEmpty()) {
            return 0;
        }

        List<Long> distinctRecipients = recipients.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        distinctRecipients.forEach(userId ->
                notificationService.notifyAuctionClosingSoon(userId, auctionId));
        return distinctRecipients.size();
    }

    @Transactional
    public boolean finalizeExpiredAuction(Long auctionId) {
        AuctionBidTarget target = findBidTarget(auctionId);
        if (!AuctionStatusCode.ACTIVE.equals(target.getAuctionStatusCode())) {
            return false;
        }
        if (target.getEndDateTime() == null || target.getEndDateTime().isAfter(databaseNow(target))) {
            return false;
        }

        String finalStatus = AuctionStatusCode.FAILED;
        if (target.getCurrentHighestBidId() != null && target.getCurrentHighestBidderId() != null) {
            pointService.convertHoldToEscrow(
                    target.getCurrentHighestBidderId(),
                    RefType.BID,
                    target.getCurrentHighestBidId(),
                    "경매 낙찰 보관금 전환");
            finalStatus = AuctionStatusCode.ENDED;
        }

        int updated = auctionMapper.updateExpiredAuctionStatus(auctionId, finalStatus, SYSTEM_ACTOR);
        if (updated == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "경매 마감 상태가 이미 변경되었습니다.");
        }
        if (AuctionStatusCode.ENDED.equals(finalStatus)) {
            createAuctionTrade(
                    target,
                    target.getCurrentHighestBidId(),
                    target.getCurrentHighestBidderId(),
                    target.getCurrentPrice(),
                    AuctionTradeSource.AUCTION_WIN,
                    target.getCurrentHighestTradeMethodCode());
            notificationService.notifyAuctionResult(
                    target.getCurrentHighestBidderId(),
                    auctionId,
                    true);
        } else {
            notificationService.notifyAuctionFailed(target.getSellerId(), auctionId);
        }
        publishAuctionChanged(auctionId, "AUCTION_FINALIZED");
        return true;
    }

    @Transactional
    public void createAuctionForProduct(
            Long productId,
            BigDecimal startAmount,
            BigDecimal bidUnitAmount,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            Long actorUserId) {
        LocalDateTime now = LocalDateTime.now();
        validateAuctionCreation(
                productId,
                startAmount,
                bidUnitAmount,
                startDateTime,
                endDateTime,
                actorUserId,
                now);

        BigDecimal configuredBidUnit = requireConfiguredBidUnit(bidUnitAmount);

        String statusCode = startDateTime.isAfter(now)
                ? AuctionStatusCode.READY
                : AuctionStatusCode.ACTIVE;
        int inserted = auctionMapper.insertAuction(
                productId,
                statusCode,
                startAmount,
                configuredBidUnit,
                startDateTime,
                endDateTime,
                actorUserId.toString());
        if (inserted == 0) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
    }

    /**
     * 담당자2 호출부가 시작일시 계약으로 전환될 때까지 유지하는 즉시 시작 호환 계약이다.
     * 예약 경매는 시작일시 없이는 올바르게 저장할 수 없으므로 새 시그니처를 사용해야 한다.
     */
    @Transactional
    public void createAuctionForProduct(
            Long productId,
            BigDecimal startAmount,
            BigDecimal bidUnitAmount,
            LocalDateTime endDateTime,
            boolean openImmediately,
            Long actorUserId) {
        if (!openImmediately) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "예약 경매는 시작일시가 필요합니다.");
        }
        createAuctionForProduct(
                productId,
                startAmount,
                bidUnitAmount,
                LocalDateTime.now(),
                endDateTime,
                actorUserId);
    }

    private AuctionDetailResponse findAuctionDetailWithProductValidation(
            Long auctionId,
            Long userId,
            boolean includeSupplemental) {
        Long productId = auctionMapper.findProductIdByAuctionId(auctionId);
        if (productId == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }

        ProductService productService = productServiceProvider.getObject();
        productService.getProduct(productId);
        return loadAuctionDetail(auctionId, userId, includeSupplemental);
    }

    private AuctionDetailResponse loadAuctionDetail(Long auctionId, Long userId) {
        return loadAuctionDetail(auctionId, userId, true);
    }

    private AuctionDetailResponse loadAuctionDetail(
            Long auctionId,
            Long userId,
            boolean includeSupplemental) {
        AuctionDetailResponse detail = auctionMapper.findAuctionDetail(auctionId, userId);
        if (detail == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        detail.setFavorite(userId != null
                && productFavoriteMapper.existsActive(detail.getProductId(), userId));
        detail.setImages(auctionMapper.findAuctionImages(detail.getProductId()));
        detail.setBids(auctionMapper.findAuctionBids(auctionId));
        if (includeSupplemental) {
            applySellerReviewSummary(detail);
            detail.setProductUpdates(loadProductUpdates(detail.getProductId()));
        }
        return detail;
    }

    private void applySellerReviewSummary(AuctionDetailResponse detail) {
        if (detail.getSellerId() == null) {
            return;
        }

        TrustScoreResponse trustScore = reviewService.getTrustScore(detail.getSellerId());
        if (trustScore == null) {
            return;
        }

        detail.setSellerRating(trustScore.getTotalScore());
        detail.setSellerReviewCount(trustScore.getTotalCount());
    }

    private List<AuctionProductUpdateItem> loadProductUpdates(Long productId) {
        ProductService productService = productServiceProvider.getIfAvailable();
        if (productService == null) {
            return List.of();
        }

        List<ProductCommentResponse> comments = productService.getComments(productId);
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }

        return comments.stream()
                .map(comment -> AuctionProductUpdateItem.builder()
                        .updateId(comment.getPrdCmtSn())
                        .title(comment.getPrdCmtTtl())
                        .content(comment.getPrdCmtCn())
                        .registeredAt(comment.getPrdCmtRegDt())
                        .updatedAt(comment.getPrdCmtUpdtDt())
                        .build())
                .toList();
    }

    @Transactional
    public AuctionDetailResponse placeBid(Long auctionId, Long userId, AuctionBidRequest request) {
        AuctionBidTarget target = findBidTarget(auctionId);
        validateBidAvailable(target, userId);
        validateNotCurrentHighestBidder(target, userId);
        String selectedTradeMethodCode = resolveSelectedTradeMethod(
                target,
                request == null ? null : request.getTradeMethod());
        validateDeliveryAddress(selectedTradeMethodCode, userId);
        AuctionPolicy policy = pointService.getAuctionPolicy();

        BigDecimal bidAmount = request == null ? null : request.getBidAmount();
        if (bidAmount == null || bidAmount.compareTo(minimumBidPrice(target)) < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateBidUnit(target, bidAmount);
        validateBidBelowInstantBuyPrice(target, bidAmount);

        int updatedCount = auctionMapper.updateAuctionCurrentPrice(auctionId, bidAmount, userId.toString());
        if (updatedCount == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "현재가가 갱신되었습니다. 다시 확인 후 입찰해주세요.");
        }

        Long previousHighestBidId = target.getCurrentHighestBidId();
        Long previousHighestBidderId = target.getCurrentHighestBidderId();

        auctionMapper.updateCurrentHighestBids(auctionId);
        AuctionBidCreateCommand bid = insertHighestBid(
                auctionId,
                userId,
                bidAmount,
                selectedTradeMethodCode);
        pointService.hold(userId, toPointAmount(bidAmount), RefType.BID, bid.getBidId(), "입찰 포인트 홀딩");
        releasePreviousHighestBidHold(previousHighestBidderId, previousHighestBidId);
        auctionMapper.extendAuctionTime(
                auctionId,
                policy.getAucExtMin(),
                policy.getAucExtMaxCnt(),
                userId.toString());
        notifyOutbidBidder(previousHighestBidderId, userId, auctionId, bidAmount);

        AuctionDetailResponse detail = loadAuctionDetail(auctionId, userId);
        publishAuctionChanged(auctionId, "BID_PLACED");
        return detail;
    }

    @Transactional
    public AuctionDetailResponse changeCurrentHighestBidTradeMethod(
            Long auctionId,
            Long userId,
            AuctionTradeMethodChangeRequest request) {
        AuctionBidTarget target = findBidTarget(auctionId);
        validateBidAvailable(target, userId);
        validateCurrentHighestBidder(target, userId);
        validateMixedTradeMethodProduct(target);

        String selectedTradeMethodCode = resolveSelectedTradeMethod(
                target,
                request == null ? null : request.getTradeMethod());
        if (selectedTradeMethodCode.equals(target.getCurrentHighestTradeMethodCode())) {
            return loadAuctionDetail(auctionId, userId);
        }

        validateDeliveryAddress(selectedTradeMethodCode, userId);
        int updatedCount = auctionMapper.updateCurrentHighestBidTradeMethod(
                auctionId,
                target.getCurrentHighestBidId(),
                userId,
                selectedTradeMethodCode,
                userId.toString());
        if (updatedCount == 0) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "최고입찰 정보가 변경되었습니다. 다시 확인해 주세요.");
        }

        AuctionDetailResponse detail = loadAuctionDetail(auctionId, userId);
        publishAuctionChanged(auctionId, "BID_TRADE_METHOD_CHANGED");
        return detail;
    }

    @Transactional
    public AuctionDetailResponse buyNow(Long auctionId, Long userId, AuctionBuyNowRequest request) {
        AuctionBidTarget target = findBidTarget(auctionId);
        validateBidAvailable(target, userId);
        String selectedTradeMethodCode = resolveSelectedTradeMethod(
                target,
                request == null ? null : request.getTradeMethod());
        validateDeliveryAddress(selectedTradeMethodCode, userId);

        BigDecimal instantBuyPrice = target.getInstantBuyPrice();
        if (instantBuyPrice == null || instantBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Long previousHighestBidId = target.getCurrentHighestBidId();
        Long previousHighestBidderId = target.getCurrentHighestBidderId();

        auctionMapper.updateCurrentHighestBids(auctionId);
        AuctionBidCreateCommand bid = insertHighestBid(
                auctionId,
                userId,
                instantBuyPrice,
                selectedTradeMethodCode);
        pointService.hold(userId, toPointAmount(instantBuyPrice), RefType.BID, bid.getBidId(), "즉시구매 포인트 홀딩");
        pointService.convertHoldToEscrow(userId, RefType.BID, bid.getBidId(), "즉시구매 보관금 전환");
        releasePreviousHighestBidHold(previousHighestBidderId, previousHighestBidId);
        int closed = auctionMapper.closeAuctionByInstantBuy(auctionId, instantBuyPrice, userId.toString());
        if (closed == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "경매 상태가 이미 변경되었습니다.");
        }
        AuctionTradeCreateResult trade = createAuctionTrade(
                target,
                bid.getBidId(),
                userId,
                instantBuyPrice,
                AuctionTradeSource.BUY_NOW,
                selectedTradeMethodCode);
        notifyOutbidBidder(previousHighestBidderId, userId, auctionId, instantBuyPrice);
        notificationService.notifyAuctionResult(userId, auctionId, true);

        AuctionDetailResponse detail = loadAuctionDetail(auctionId, userId);
        detail.setTradeId(trade.getTradeSn());
        publishAuctionChanged(auctionId, "BUY_NOW");
        return detail;
    }

    private AuctionTradeCreateResult createAuctionTrade(
            AuctionBidTarget target,
            Long winningBidId,
            Long buyerUserId,
            BigDecimal tradeAmount,
            AuctionTradeSource source,
            String selectedTradeMethodCode) {
        return tradeService.createAuctionTrade(
                new AuctionTradeCreateCommand(
                        target.getAuctionId(),
                        target.getProductId(),
                        winningBidId,
                        target.getSellerId(),
                        buyerUserId,
                        tradeAmount,
                        source,
                        selectedTradeMethodCode));
    }

    private void publishAuctionChanged(Long auctionId, String eventType) {
        auctionEventPublisher.publishAfterCommit(new AuctionRealtimeEvent(auctionId, eventType));
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
        if (target.getEndDateTime() != null && !target.getEndDateTime().isAfter(databaseNow(target))) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        if (target.getSellerId() != null && target.getSellerId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateDeliveryAddress(String selectedTradeMethodCode, Long userId) {
        if (DELIVERY_TRADE_METHOD_CODE.equals(selectedTradeMethodCode)) {
            memberService.getBuyerAddressSnapshot(userId);
        }
    }

    private String resolveSelectedTradeMethod(AuctionBidTarget target, String requestedTradeMethodCode) {
        String productTradeMethodCode = target.getTradeMethodCode();
        if (DELIVERY_TRADE_METHOD_CODE.equals(productTradeMethodCode)
                || OFFLINE_TRADE_METHOD_CODE.equals(productTradeMethodCode)) {
            if (requestedTradeMethodCode == null
                    || requestedTradeMethodCode.isBlank()
                    || productTradeMethodCode.equals(requestedTradeMethodCode)) {
                return productTradeMethodCode;
            }
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "상품의 거래방식과 선택한 거래방식이 일치하지 않습니다.");
        }

        if (BOTH_TRADE_METHOD_CODE.equals(productTradeMethodCode)
                && (DELIVERY_TRADE_METHOD_CODE.equals(requestedTradeMethodCode)
                || OFFLINE_TRADE_METHOD_CODE.equals(requestedTradeMethodCode))) {
            return requestedTradeMethodCode;
        }

        throw new CustomException(
                ErrorCode.INVALID_INPUT_VALUE,
                "배송 또는 직거래 방식을 선택해 주세요.");
    }

    private void validateNotCurrentHighestBidder(AuctionBidTarget target, Long userId) {
        if (target.getCurrentHighestBidderId() != null && target.getCurrentHighestBidderId().equals(userId)) {
            throw new CustomException(ErrorCode.CONFLICT, "현재 최고 입찰자입니다.");
        }
    }

    private void validateCurrentHighestBidder(AuctionBidTarget target, Long userId) {
        if (target.getCurrentHighestBidId() == null
                || target.getCurrentHighestBidderId() == null
                || !target.getCurrentHighestBidderId().equals(userId)) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "현재 최고입찰자만 거래방식을 변경할 수 있습니다.");
        }
    }

    private void validateMixedTradeMethodProduct(AuctionBidTarget target) {
        if (!BOTH_TRADE_METHOD_CODE.equals(target.getTradeMethodCode())) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "배송과 직거래를 모두 지원하는 경매만 거래방식을 변경할 수 있습니다.");
        }
    }

    private LocalDateTime databaseNow(AuctionBidTarget target) {
        return target.getDatabaseNow() == null ? LocalDateTime.now() : target.getDatabaseNow();
    }

    private BigDecimal minimumBidPrice(AuctionBidTarget target) {
        BigDecimal currentPrice = target.getCurrentPrice() == null ? BigDecimal.ZERO : target.getCurrentPrice();
        return currentPrice.add(effectiveBidUnit(target));
    }

    private BigDecimal effectiveBidUnit(AuctionBidTarget target) {
        BigDecimal bidUnitPrice = target.getBidUnitPrice();
        if (bidUnitPrice == null || bidUnitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "경매 입찰 단위가 올바르지 않습니다.");
        }
        return bidUnitPrice;
    }

    private void validateBidUnit(AuctionBidTarget target, BigDecimal bidAmount) {
        BigDecimal currentPrice = target.getCurrentPrice() == null ? BigDecimal.ZERO : target.getCurrentPrice();
        BigDecimal bidIncrement = bidAmount.subtract(currentPrice);
        if (bidIncrement.remainder(effectiveBidUnit(target)).compareTo(BigDecimal.ZERO) != 0) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "입찰 금액은 입찰 단위의 배수여야 합니다."
            );
        }
    }

    private BigDecimal requireConfiguredBidUnit(BigDecimal bidUnitAmount) {
        if (bidUnitAmount == null || bidUnitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "입찰 단위를 선택해 주세요.");
        }

        boolean configured = referenceDataService.getActiveCodes(BID_UNIT_GROUP_CODE).stream()
                .map(CommonCode::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(this::parseConfiguredBidUnit)
                .anyMatch(option -> option.compareTo(bidUnitAmount) == 0);
        if (!configured) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "선택할 수 없는 입찰 단위입니다.");
        }
        return bidUnitAmount;
    }

    private BigDecimal parseConfiguredBidUnit(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "입찰 단위 공통코드 설정이 올바르지 않습니다.");
        }
    }

    private void validateBidBelowInstantBuyPrice(AuctionBidTarget target, BigDecimal bidAmount) {
        BigDecimal instantBuyPrice = target.getInstantBuyPrice();
        if (instantBuyPrice != null
                && instantBuyPrice.compareTo(BigDecimal.ZERO) > 0
                && bidAmount.compareTo(instantBuyPrice) >= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "입찰 금액은 즉시구매가보다 낮아야 합니다.");
        }
    }

    private AuctionBidCreateCommand insertHighestBid(
            Long auctionId,
            Long userId,
            BigDecimal bidAmount,
            String selectedTradeMethodCode) {
        AuctionBidCreateCommand bid = new AuctionBidCreateCommand(
                auctionId,
                userId,
                bidAmount,
                BidStatusCode.HIGHEST,
                selectedTradeMethodCode,
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
        pointService.releaseHold(
                previousHighestBidderId,
                RefType.BID,
                previousHighestBidId,
                "상위 입찰 발생에 따른 기존 입찰 홀딩 반환");
    }

    private void notifyOutbidBidder(
            Long previousHighestBidderId,
            Long newHighestBidderId,
            Long auctionId,
            BigDecimal newPrice) {
        if (previousHighestBidderId == null || previousHighestBidderId.equals(newHighestBidderId)) {
            return;
        }
        notificationService.notifyBidUpdated(
                previousHighestBidderId,
                auctionId,
                toPointAmount(newPrice));
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
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            Long actorUserId,
            LocalDateTime now) {
        if (productId == null
                || actorUserId == null
                || startAmount == null
                || startDateTime == null
                || endDateTime == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (startAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (bidUnitAmount != null && bidUnitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!endDateTime.isAfter(now) || !endDateTime.isAfter(startDateTime)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void normalize(AuctionListRequest request) {
        if (request.getSellerId() != null && request.getSellerId() <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "판매자 번호가 올바르지 않습니다.");
        }
        if (request.isIncludeHistory() && request.getSellerId() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "판매자 히스토리 조회에는 판매자 번호가 필요합니다.");
        }
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
        request.setTradeMethodCodes(resolveTradeMethodCodes(request.getTradeMethod()));

        List<String> statuses = request.getStatus();
        boolean hasStatusFilter = statuses != null && !statuses.isEmpty();
        request.setHasStatusFilter(hasStatusFilter);
        request.setStatusReady(!hasStatusFilter
                || statuses.contains("ready")
                || statuses.contains(AuctionStatusCode.READY));
        request.setStatusActive(!hasStatusFilter
                || statuses.contains("active")
                || statuses.contains(AuctionStatusCode.ACTIVE));
        request.setStatusEnded(!hasStatusFilter
                || statuses.contains("ended")
                || statuses.contains(AuctionStatusCode.ENDED));
        request.setStatusEndingSoon(hasStatusFilter && statuses.contains("endingSoon"));
    }

    private List<String> resolveTradeMethodCodes(String tradeMethod) {
        return switch (tradeMethod) {
            case "delivery", DELIVERY_TRADE_METHOD_CODE ->
                List.of(DELIVERY_TRADE_METHOD_CODE, BOTH_TRADE_METHOD_CODE);
            case "direct", OFFLINE_TRADE_METHOD_CODE ->
                List.of(OFFLINE_TRADE_METHOD_CODE, BOTH_TRADE_METHOD_CODE);
            case BOTH_TRADE_METHOD_CODE -> List.of(BOTH_TRADE_METHOD_CODE);
            default -> List.of();
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
