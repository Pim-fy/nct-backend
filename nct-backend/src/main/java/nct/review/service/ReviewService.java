package nct.review.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import nct.auction.service.AuctionService;
import nct.common.domain.RefType;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.provider.port.ProviderReviewRatingPort;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.review.constant.CounterpartReviewStatus;
import nct.review.constant.MyReviewStatus;
import nct.review.constant.ReviewDomainCode;
import nct.review.domain.Review;
import nct.review.domain.ReviewImage;
import nct.review.dto.CounterpartTradeReviewResponse;
import nct.review.dto.MyReviewItem;
import nct.review.dto.MyTradeReviewResponse;
import nct.review.dto.ReviewCreateResult;
import nct.review.dto.ReviewRouteContext;
import nct.review.dto.ReviewRatingTarget;
import nct.review.dto.ReviewUpdateResult;
import nct.review.dto.ServiceReviewRatingSummary;
import nct.review.dto.TradeReviewStateSource;
import nct.review.dto.TrustScoreResponse;
import nct.review.dto.UserReviewItem;
import nct.review.dto.WritableTradeItem;
import nct.review.exception.InvalidRatingException;
import nct.review.exception.ReviewNotFoundException;
import nct.review.exception.TooManyReviewPhotosException;
import nct.review.exception.TradeNotReviewableException;
import nct.review.mapper.ReviewImageMapper;
import nct.review.mapper.ReviewMapper;

/**
 * [리뷰 - 서비스]
 * - "작성 가능한 리뷰" 조회, "내가 쓴 리뷰" 조회, 리뷰 등록(+사진 첨부)을 담당한다.
 * - TRADE 관련 검증은 ReviewMapper의 임시 조회에 의존한다 (자세한 사유는
 * nct.review.constant.TempTradeCode, ReviewMapper.xml 상단 주석 참고).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    /** 리뷰 사진 최대 장수 (2026-07-22 확정) — 신규 등록·수정 추가 합산 기준 */
    private static final int MAX_REVIEW_PHOTOS = 5;

    private final ReviewMapper reviewMapper;
    private final FileStorageService fileStorageService;
    private final ReviewImageMapper reviewImageMapper;
    private final NotificationService notificationService;
    private final ProviderReviewRatingPort providerReviewRatingPort;
    // @ai_generated (담당자1 황희준, 2026-08-07, 조율 대기): AuctionService가 이미 ReviewService를
    // 주입받고 있어(신뢰지표 소비) 순환 의존을 피하려고 ObjectProvider로 지연 주입한다.
    // AuctionService.productServiceProvider와 같은 패턴이다.
    private final ObjectProvider<AuctionService> auctionServiceProvider;

    /** 작성 가능한 리뷰 목록 (완료된 거래 중 아직 리뷰를 안 쓴 것) */
    public List<WritableTradeItem> getWritableTrades(long usrSn) {
        List<WritableTradeItem> items = reviewMapper.selectWritableTrades(usrSn);
        Map<Long, Long> auctionIdsByProductId = resolveAuctionIds(
                items.stream().map(WritableTradeItem::getProductId).toList());
        return items.stream()
                .map(item -> item.toBuilder()
                        .auctionId(auctionIdsByProductId.get(item.getProductId()))
                        .build())
                .toList();
    }

    /** 내가 작성한 리뷰 목록 (사진 URL까지 채워서 반환) */
    public List<MyReviewItem> getMyReviews(long usrSn) {
        List<MyReviewItem> items = reviewMapper.selectMyReviews(usrSn);
        Map<Long, Long> auctionIdsByProductId = resolveAuctionIds(
                items.stream().map(MyReviewItem::getProductId).toList());
        // 리뷰마다 사진을 별도 조회한다 (목록이 최대 100건이라 N+1이어도 지금 단계에서는 허용 범위).
        return items.stream()
                .map(item -> item.toBuilder()
                        .auctionId(auctionIdsByProductId.get(item.getProductId()))
                        .photos(reviewImageMapper.selectUrlsByReviewSn(item.getId()))
                        .build())
                .toList();
    }

    // @ai_generated (담당자1 황희준, 2026-08-07, 조율 대기): AUCTION을 Mapper에서 직접 JOIN하지
    // 않고, AuctionService의 배치 계약으로 PRD_SN -> AUC_SN을 얻는다.
    private Map<Long, Long> resolveAuctionIds(List<Long> productIds) {
        List<Long> distinctProductIds = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctProductIds.isEmpty()) {
            // @ai_generated (담당자1, 2026-08-07): Map.of()는 get(null)에서 NPE를 던지지만
            // (서비스 거래 항목은 productId가 항상 null이라 이 조회가 흔하다), Collections.emptyMap()은
            // null 키 조회를 안전하게 null로 돌려준다.
            return Collections.emptyMap();
        }
        return auctionServiceProvider.getObject().findAuctionIdsByProductIds(distinctProductIds);
    }

    /** 거래 상세에서 현재 로그인 사용자의 리뷰 상태를 DB 상태 추가 없이 계산한다. */
    @Transactional(readOnly = true)
    // @ai_generated: 거래·활성 리뷰·삭제 이력을 조합해 저장하지 않는 화면 상태를 계산한다.
    public MyTradeReviewResponse getMyTradeReview(long usrSn, long tradeId) {
        if (usrSn <= 0 || tradeId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "거래번호와 회원번호가 올바르지 않습니다.");
        }

        TradeReviewStateSource source = reviewMapper.selectMyTradeReviewState(tradeId, usrSn);
        if (source == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "존재하지 않거나 접근할 수 없는 거래입니다.");
        }

        if (source.getReviewId() != null && "Y".equals(source.getReviewUseYn())) {
            return MyTradeReviewResponse.builder()
                    .status(MyReviewStatus.WRITTEN)
                    .tradeId(source.getTradeId())
                    .reviewId(source.getReviewId())
                    .rating(source.getRating())
                    .content(source.getContent())
                    .photos(reviewImageMapper.selectUrlsByReviewSn(source.getReviewId()))
                    .build();
        }

        MyReviewStatus status = source.getReviewId() == null && "TRDC0006".equals(source.getTradeStatusCode())
                ? MyReviewStatus.WRITABLE
                : MyReviewStatus.UNAVAILABLE;

        return MyTradeReviewResponse.builder()
                .status(status)
                .tradeId(source.getTradeId())
                .build();
    }

    /** 거래 상세에서 상대방이 나에 대해 작성한 리뷰를 조회한다. */
    @Transactional(readOnly = true)
    public CounterpartTradeReviewResponse getCounterpartTradeReview(long usrSn, long tradeId) {
        if (usrSn <= 0 || tradeId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "거래번호와 회원번호가 올바르지 않습니다.");
        }

        TradeReviewStateSource source = reviewMapper.selectCounterpartTradeReviewState(tradeId, usrSn);
        if (source == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "존재하지 않거나 접근할 수 없는 거래입니다.");
        }

        if (source.getReviewId() != null && "Y".equals(source.getReviewUseYn())) {
            return CounterpartTradeReviewResponse.builder()
                    .status(CounterpartReviewStatus.WRITTEN)
                    .tradeId(source.getTradeId())
                    .rating(source.getRating())
                    .content(source.getContent())
                    .photos(reviewImageMapper.selectUrlsByReviewSn(source.getReviewId()))
                    .build();
        }

        return CounterpartTradeReviewResponse.builder()
                .status(CounterpartReviewStatus.NOT_WRITTEN)
                .tradeId(source.getTradeId())
                .build();
    }

    // @ai_generated: 소유한 활성 물건 리뷰만 기존 URL 변환 대상으로 허용한다.
    @Transactional(readOnly = true)
    public ReviewRouteContext getMyReviewRouteContext(long usrSn, long reviewId) {
        if (usrSn <= 0 || reviewId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "리뷰번호와 회원번호가 올바르지 않습니다.");
        }

        ReviewRouteContext context = reviewMapper.selectMyReviewRouteContext(reviewId, usrSn);
        if (context == null) {
            throw new ReviewNotFoundException(reviewId);
        }
        // @ai_generated (담당자1 황희준, 2026-08-07, 조율 대기): AUCTION을 Mapper에서 직접
        // JOIN하지 않고 AuctionService 계약으로 auctionId를 채운다. 예전에는 AUCTION INNER JOIN이
        // 실패하면 이 조회 자체가 통째로 실패(404)했다 - 상품에 대응하는 AUCTION 행이 없는 경우도
        // 동일하게 404로 유지해야, 프론트가 auctionId=null을 그대로 받아 "/auction/null/trade"로
        // 이동하는 걸 막을 수 있다.
        Long auctionId = auctionServiceProvider.getObject().findAuctionIdByProductId(context.getProductId());
        if (auctionId == null) {
            throw new ReviewNotFoundException(reviewId);
        }
        context.setAuctionId(auctionId);
        return context;
    }

    /**
     * 리뷰 등록.
     *
     * @param tradeId 리뷰 대상 거래 (클라이언트가 보낸 값 - 서버가 아래에서 다시 검증한다)
     * @param rating  평점 1~5
     * @param content 리뷰 내용
     * @param photos  첨부 사진 (없어도 됨)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewCreateResult createReview(long usrSn, long tradeId, int rating, String content,
            List<MultipartFile> photos, String reviewerNickname) {
        if (rating < 1 || rating > 5) {
            throw new InvalidRatingException(rating);
        }
        validatePhotoCount(0, photos);

        // 서버 측 재검증 - 클라이언트가 보낸 tradeId를 그대로 믿지 않는다
        // (BidService의 "서버 측 재검증" 원칙과 동일: 완료 여부·참여자 여부·중복 작성 여부를 다시 확인).
        WritableTradeItem trade = reviewMapper.selectWritableTrade(tradeId, usrSn)
                .orElseThrow(() -> new TradeNotReviewableException(tradeId));

        String domainCd = "goods".equals(trade.getDealType()) ? ReviewDomainCode.GOODS : ReviewDomainCode.SERVICE;

        Review review = Review.builder()
                .trdSn(tradeId)
                .revwrUsrSn(usrSn)
                .revwdUsrSn(trade.getCounterpartUsrSn())
                .rvwDomainCd(domainCd)
                .rvwScore(rating)
                .rvwCn(content)
                .build();
        try {
            reviewMapper.insertReview(review);
        } catch (DuplicateKeyException ex) {
            // @ai_generated: 동시 등록 경쟁에서 DB 유니크 제약이 이긴 경우도 작성 불가 계약으로 통일한다.
            throw new TradeNotReviewableException(tradeId);
        }

        int photoCount = 0;
        if (photos != null && !photos.isEmpty()) {
            photoCount = storeReviewImages(photos, review.getRvwSn(), usrSn);
        }

        synchronizeProviderRating(
                domainCd,
                trade.getCounterpartUsrSn(),
                trade.isCounterpartServiceProvider());

        notificationService.notify(
                trade.getCounterpartUsrSn(),
                NotificationType.TRADE,
                NotificationDomain.TRADE,
                "리뷰가 등록되었습니다",
                reviewerNickname + "님이 리뷰를 등록했습니다.",
                RefType.TRADE,
                tradeId);

        return ReviewCreateResult.builder()
                .id(review.getRvwSn())
                .tradeId(tradeId)
                .rating(rating)
                .photoCount(photoCount)
                .build();
    }

    /**
     * 리뷰 수정 (평점·내용). 본인이 작성한 리뷰만 수정할 수 있다.
     * 새 사진은 기존 첨부에 추가된다 - 프론트(ReviewEditPage)가 기존 사진 목록을 다시 보여주지 않고
     * 이번에 새로 고른 파일만 넘겨주는 구조라, 여기서도 기존 첨부를 건드리지 않고 더하기만 한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewUpdateResult updateReview(long usrSn, long rvwSn, int rating, String content,
            List<MultipartFile> photos) {
        if (rating < 1 || rating > 5) {
            throw new InvalidRatingException(rating);
        }
        ReviewRatingTarget ratingTarget = reviewMapper.selectOwnedActiveReviewRatingTarget(rvwSn, usrSn)
                .orElseThrow(() -> new ReviewNotFoundException(rvwSn));
        // 수정은 새 사진이 기존 첨부에 "추가"되는 구조라, 기존 개수까지 합산해서 검사한다
        int existingPhotoCount = reviewImageMapper.selectUrlsByReviewSn(rvwSn).size();
        validatePhotoCount(existingPhotoCount, photos);

        int updated = reviewMapper.updateReview(rvwSn, usrSn, rating, content);
        if (updated == 0) {
            throw new ReviewNotFoundException(rvwSn);
        }

        int addedPhotoCount = 0;
        if (photos != null && !photos.isEmpty()) {
            addedPhotoCount = storeReviewImages(photos, rvwSn, usrSn);
        }

        synchronizeProviderRating(
                ratingTarget.getDomainCode(),
                ratingTarget.getReceiverUserSn(),
                ratingTarget.isReceivedAsServiceProvider());

        return ReviewUpdateResult.builder()
                .id(rvwSn)
                .rating(rating)
                .addedPhotoCount(addedPhotoCount)
                .build();
    }

    /** 리뷰 삭제 (소프트 삭제). 본인이 작성한 리뷰만 삭제할 수 있다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteReview(long usrSn, long rvwSn) {
        ReviewRatingTarget ratingTarget = reviewMapper.selectOwnedActiveReviewRatingTarget(rvwSn, usrSn)
                .orElseThrow(() -> new ReviewNotFoundException(rvwSn));
        int deleted = reviewMapper.deleteReview(rvwSn, usrSn);
        if (deleted == 0) {
            throw new ReviewNotFoundException(rvwSn);
        }
        synchronizeProviderRating(
                ratingTarget.getDomainCode(),
                ratingTarget.getReceiverUserSn(),
                ratingTarget.isReceivedAsServiceProvider());
    }

    /**
     * 특정 회원이 받은 리뷰 목록 (F-COM-008, 담당자4 정민재 소비).
     * 작성자 닉네임은 USERS.USR_NM 값을 그대로 반환한다.
     *
     * @param dealType "goods" | "service" | null(전체)
     * @param page     0-indexed 페이지 번호
     * @param size     페이지당 건수 (최대 50 강제)
     */
    public PageResponse<UserReviewItem> getReviewsAboutUser(
            long usrSn,
            String dealType,
            String role,
            int page,
            int size) {
        String normalizedRole = normalizeReceivedGoodsRole(dealType, role);
        int safeSize = Math.min(size, 50);
        int offset = page * safeSize;

        List<UserReviewItem> reviews = reviewMapper.selectReviewsByReceiver(
                usrSn,
                dealType,
                normalizedRole,
                offset,
                safeSize);

        long total = reviewMapper.countReviewsByReceiver(usrSn, dealType, normalizedRole);
        return PageResponse.<UserReviewItem>builder()
                .content(reviews)
                .totalCount(total)
                .page(page)
                .size(safeSize)
                .hasNext((long)(page + 1) * safeSize < total)
                .build();
    }

    /**
     * 특정 회원의 신뢰지표 (F-COM-009~010, 담당자4 정민재 소비).
     * 리뷰가 없으면 totalCount=0, 점수는 null, hasReviews=false.
     */
    public TrustScoreResponse getTrustScore(long usrSn) {
        TrustScoreResponse raw = reviewMapper.selectTrustScore(usrSn);
        return raw.toBuilder()
                .usrSn(usrSn)
                .hasReviews(raw.getTotalCount() > 0)
                .build();
    }

    /** 담당자 7 · F-COM-008: 역할 필터는 물품 리뷰에서만 허용하고 ALL은 기존 전체 조회로 정규화한다. */
    private String normalizeReceivedGoodsRole(String dealType, String role) {
        if (role == null || role.isBlank() || "ALL".equalsIgnoreCase(role.trim())) {
            return null;
        }

        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        if (!"SELLER".equals(normalizedRole) && !"BUYER".equals(normalizedRole)) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "리뷰 역할은 ALL, SELLER, BUYER만 사용할 수 있습니다.");
        }
        if (!"goods".equals(dealType)) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "판매자/구매자 역할 필터는 물품 리뷰에서만 사용할 수 있습니다.");
        }
        return normalizedRole;
    }

    /** 서비스 제공자로서 받은 활성 리뷰만 제공자 검색·프로필용 캐시에 반영한다. */
    private void synchronizeProviderRating(
            String domainCode,
            long receiverUserSn,
            boolean receivedAsServiceProvider) {
        if (!ReviewDomainCode.SERVICE.equals(domainCode) || !receivedAsServiceProvider) {
            return;
        }
        if (!providerReviewRatingPort.lockReviewRating(receiverUserSn)) {
            return;
        }
        ServiceReviewRatingSummary summary = reviewMapper.selectServiceReviewRatingSummary(receiverUserSn);
        providerReviewRatingPort.updateReviewRating(
                receiverUserSn,
                summary.getAverageScore(),
                summary.getReviewCount());
    }

    /** 기존 개수 + 이번에 새로 올리는 개수(빈 파일 제외)가 MAX_REVIEW_PHOTOS를 넘으면 거부 */
    private void validatePhotoCount(int existingCount, List<MultipartFile> photos) {
        int newCount = photos == null ? 0
                : (int) photos.stream().filter(p -> p != null && !p.isEmpty()).count();
        if (existingCount + newCount > MAX_REVIEW_PHOTOS) {
            throw new TooManyReviewPhotosException(existingCount, newCount, MAX_REVIEW_PHOTOS);
        }
    }

    /** 사진 목록을 FILES에 저장하고 REVIEW_IMAGE에 연결한다. 저장된 건수를 반환한다. */
    private int storeReviewImages(List<MultipartFile> photos, long rvwSn, long usrSn) {
        if (photos == null || photos.isEmpty()) {
            return 0;
        }
        List<ReviewImage> images = new ArrayList<>();
        int sortNo = 0;
        for (MultipartFile photo : photos) {
            if (photo == null || photo.isEmpty()) continue;
            FileMeta fileMeta = fileStorageService.storeImage(photo, "review", usrSn);
            images.add(ReviewImage.builder()
                    .rvwSn(rvwSn)
                    .flSn(fileMeta.getFlSn())
                    .rvwImgSortNo(sortNo++)
                    .build());
        }
        if (!images.isEmpty()) {
            reviewImageMapper.insertAll(images);
        }
        return images.size();
    }

}
