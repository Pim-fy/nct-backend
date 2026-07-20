package nct.review.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.file.service.FileStorageService;
import nct.review.constant.ReviewDomainCode;
import nct.review.domain.Review;
import nct.review.dto.MyReviewItem;
import nct.review.dto.ReviewCreateResult;
import nct.review.dto.ReviewUpdateResult;
import nct.review.dto.WritableTradeItem;
import nct.review.exception.InvalidRatingException;
import nct.review.exception.ReviewNotFoundException;
import nct.review.exception.TradeNotReviewableException;
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

    private final ReviewMapper reviewMapper;
    private final FileStorageService fileStorageService;

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
                        .photos(fileStorageService.getUrls(RefType.REVIEW, item.getId()))
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

        // 서버 측 재검증 - 클라이언트가 보낸 tradeId를 그대로 믿지 않는다
        // (BidService의 "서버 측 재검증" 원칙과 동일: 완료 여부·참여자 여부·중복 작성 여부를 다시 확인).
        // 이 조회 결과에 이미 counterpartUsrSn(리뷰 대상자)까지 포함되어 있어 별도 조회가 필요 없다.
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

        int photoCount = photos == null ? 0 : (int) photos.stream().filter(f -> !f.isEmpty()).count();
        if (photoCount > 0) {
            fileStorageService.attach(photos, RefType.REVIEW, review.getRvwSn(), usrSn);
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

        int updated = reviewMapper.updateReview(rvwSn, usrSn, rating, content);
        if (updated == 0) {
            throw new ReviewNotFoundException(rvwSn);
        }

        int addedPhotoCount = photos == null ? 0 : (int) photos.stream().filter(f -> !f.isEmpty()).count();
        if (addedPhotoCount > 0) {
            fileStorageService.attach(photos, RefType.REVIEW, rvwSn, usrSn);
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
}
