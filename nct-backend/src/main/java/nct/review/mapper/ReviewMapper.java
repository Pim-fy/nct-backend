package nct.review.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.review.domain.Review;
import nct.review.dto.MyReviewItem;
import nct.review.dto.TrustScoreResponse;
import nct.review.dto.UserReviewItem;
import nct.review.dto.WritableTradeItem;

/**
 * [리뷰 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/review/ReviewMapper.xml 에 있다.
 * - insertReview 만 REVIEW 테이블에 쓰고, 나머지(selectWritableTrade*)는 TRADE/PRODUCT/
 *   SERVICE_REQUEST/USERS 를 전부 읽기 전용으로만 조회한다. 이 읽기 전용 조회는 TRADE 모듈이
 *   아직 없어서 임시로 직접 SQL을 짠 것이다 - 자세한 사유는 constant/TempTradeCode.java 참고.
 */
@Mapper
public interface ReviewMapper {

    /** 로그인 사용자가 리뷰를 작성할 수 있는 완료 거래 목록 (이미 작성한 건 제외) */
    List<WritableTradeItem> selectWritableTrades(@Param("usrSn") long usrSn);

    /**
     * 특정 거래 1건이 "지금 이 사용자가 리뷰 작성 가능한 상태"인지 다시 확인한다.
     * POST /api/reviews 에서 클라이언트가 보낸 tradeId를 그대로 믿지 않고 서버가 재검증할 때 쓴다.
     */
    Optional<WritableTradeItem> selectWritableTrade(@Param("tradeId") long tradeId, @Param("usrSn") long usrSn);

    /** 리뷰 1건 저장 */
    int insertReview(Review review);

    /** 로그인 사용자가 작성한 리뷰 목록 (최신순) */
    List<MyReviewItem> selectMyReviews(@Param("usrSn") long usrSn);

    /** 리뷰 평점·내용 수정 (본인 소유·미삭제 리뷰만, 영향 행 0건이면 대상 없음/타인 소유/이미 삭제) */
    int updateReview(@Param("rvwSn") long rvwSn, @Param("usrSn") long usrSn,
                      @Param("rating") int rating, @Param("content") String content);

    /** 리뷰 소프트 삭제 (RVW_USE_YN='N', 본인 소유·미삭제 리뷰만) */
    int deleteReview(@Param("rvwSn") long rvwSn, @Param("usrSn") long usrSn);

    /**
     * 특정 회원이 받은 리뷰 목록 (F-COM-008, 담당자4 정민재 소비).
     * dealType이 null이면 전체, "goods"/"service"이면 해당 거래유형만.
     */
    List<UserReviewItem> selectReviewsByReceiver(
            @Param("usrSn") long usrSn,
            @Param("dealType") String dealType,
            @Param("offset") int offset,
            @Param("size") int size);

    /** 페이지네이션 totalCount용 — selectReviewsByReceiver와 동일 조건 */
    long countReviewsByReceiver(
            @Param("usrSn") long usrSn,
            @Param("dealType") String dealType);

    /** 신뢰지표 집계 (F-COM-009~010, 담당자4 정민재 소비) */
    TrustScoreResponse selectTrustScore(@Param("usrSn") long usrSn);
}
