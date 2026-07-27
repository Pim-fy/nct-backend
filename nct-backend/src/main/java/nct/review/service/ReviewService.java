package nct.review.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.response.PageResponse;
import nct.review.constant.ReviewDomainCode;
import nct.review.domain.Review;
import nct.review.domain.ReviewImage;
import nct.review.dto.MyReviewItem;
import nct.review.dto.ReviewCreateResult;
import nct.review.dto.ReviewUpdateResult;
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

    /** 작성 가능한 리뷰 목록 (완료된 거래 중 아직 리뷰를 안 쓴 것) */
    public List<WritableTradeItem> getWritableTrades(long usrSn) {
        return reviewMapper.selectWritableTrades(usrSn);
    }

    /** 내가 작성한 리뷰 목록 (사진 URL까지 채워서 반환) */
    public List<MyReviewItem> getMyReviews(long usrSn) {
        List<MyReviewItem> items = reviewMapper.selectMyReviews(usrSn);
        // 리뷰마다 사진을 별도 조회한다 (목록이 최대 100건이라 N+1이어도 지금 단계에서는 허용 범위).
        return items.stream()
                .map(item -> item.toBuilder()
                        .photos(reviewImageMapper.selectUrlsByReviewSn(item.getId()))
                        .build())
                .toList();
    }

    /**
     * 리뷰 등록.
     *
     * @param tradeId 리뷰 대상 거래 (클라이언트가 보낸 값 - 서버가 아래에서 다시 검증한다)
     * @param rating  평점 1~5
     * @param content 리뷰 내용
     * @param photos  첨부 사진 (없어도 됨)
     */
    @Transactional
    public ReviewCreateResult createReview(long usrSn, long tradeId, int rating, String content,
            List<MultipartFile> photos) {
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
        reviewMapper.insertReview(review);

        int photoCount = 0;
        if (photos != null && !photos.isEmpty()) {
            photoCount = storeReviewImages(photos, review.getRvwSn(), usrSn);
        }

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
    @Transactional
    public ReviewUpdateResult updateReview(long usrSn, long rvwSn, int rating, String content,
            List<MultipartFile> photos) {
        if (rating < 1 || rating > 5) {
            throw new InvalidRatingException(rating);
        }
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

        return ReviewUpdateResult.builder()
                .id(rvwSn)
                .rating(rating)
                .addedPhotoCount(addedPhotoCount)
                .build();
    }

    /** 리뷰 삭제 (소프트 삭제). 본인이 작성한 리뷰만 삭제할 수 있다. */
    @Transactional
    public void deleteReview(long usrSn, long rvwSn) {
        int deleted = reviewMapper.deleteReview(rvwSn, usrSn);
        if (deleted == 0) {
            throw new ReviewNotFoundException(rvwSn);
        }
    }

    /**
     * 특정 회원이 받은 리뷰 목록 (F-COM-008, 담당자4 정민재 소비).
     * 작성자 이름은 마스킹 처리(홍*동)해서 반환한다.
     *
     * @param dealType "goods" | "service" | null(전체)
     * @param page     0-indexed 페이지 번호
     * @param size     페이지당 건수 (최대 50 강제)
     */
    public PageResponse<UserReviewItem> getReviewsAboutUser(long usrSn, String dealType, int page, int size) {
        int safeSize = Math.min(size, 50);
        int offset = page * safeSize;

        List<UserReviewItem> raw = reviewMapper.selectReviewsByReceiver(usrSn, dealType, offset, safeSize);
        List<UserReviewItem> masked = raw.stream()
                .map(item -> item.toBuilder().reviewerName(maskName(item.getReviewerName())).build())
                .toList();

        long total = reviewMapper.countReviewsByReceiver(usrSn, dealType);
        return PageResponse.<UserReviewItem>builder()
                .content(masked)
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

    /** 한국식 이름 마스킹: 홍길동→홍*동, 홍길→홍*, 홍→홍 */
    private String maskName(String name) {
        if (name == null || name.length() <= 1) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*" + name.charAt(name.length() - 1);
    }
}
