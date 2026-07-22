package nct.review.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.review.domain.ReviewImage;

/**
 * [리뷰 이미지 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/review/ReviewImageMapper.xml
 */
@Mapper
public interface ReviewImageMapper {

    /** 리뷰 등록·수정 시 이미지 목록 일괄 추가 */
    void insertAll(@Param("images") List<ReviewImage> images);

    /** 리뷰에 붙은 사진 URL 목록 (정렬순서순) */
    List<String> selectUrlsByReviewSn(@Param("rvwSn") Long rvwSn);
}
