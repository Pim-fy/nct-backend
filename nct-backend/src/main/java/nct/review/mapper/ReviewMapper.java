package nct.review.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.review.domain.Review;
import nct.review.dto.MyReviewItem;
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
}
