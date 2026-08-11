package nct.provider.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.provider.dto.ProviderCategorySearchRow;
import nct.provider.dto.ProviderProfileResponse;
import nct.provider.dto.ProviderProfileSearchItem;

/** 담당자 7 · F-PROV-004/F-COM-002: 제공자 프로필 저장과 공개 조회 모델을 다루는 MyBatis 계약이다. */
@Mapper
public interface ProviderProfileMapper {
    Optional<ProviderProfileResponse> findActiveByUserSn(@Param("userSn") Long userSn);

    /** 담당자 7 F-PROV-013: 프로필 작성 여부와 무관하게 실제 활성 서비스 분야 권한을 조회한다. */
    List<String> findActiveCategoryNames(@Param("userSn") Long userSn);

    /** 담당자 7 · F-COM-002: 활성 계정·프로필과 승인 서비스 카테고리를 기준으로 검색한다. */
    List<ProviderProfileSearchItem> searchApprovedProfiles(
            @Param("keyword") String keyword,
            @Param("categorySn") Long categorySn,
            @Param("region") String region,
            @Param("sort") String sort,
            @Param("offset") long offset,
            @Param("size") int size);

    long countApprovedProfiles(
            @Param("keyword") String keyword,
            @Param("categorySn") Long categorySn,
            @Param("region") String region);

    List<ProviderCategorySearchRow> findApprovedCategoriesByProviderUserSns(
            @Param("providerUserSns") List<Long> providerUserSns);

    int upsert(@Param("userSn") Long userSn, @Param("introduction") String introduction,
               @Param("availableArea") String availableArea, @Param("actorId") String actorId);

    /** 담당자 7 · F-COM-009: 원천 리뷰 집계값으로 검색용 평점 캐시를 갱신한다. */
    int updateReviewRating(
            @Param("userSn") long userSn,
            @Param("averageScore") BigDecimal averageScore,
            @Param("reviewCount") long reviewCount,
            @Param("actorId") String actorId);

    /** 동시 리뷰 변경이 같은 제공자 캐시를 오래된 값으로 덮지 않도록 대상 행을 직렬화한다. */
    Long lockReviewRatingByUserSn(@Param("userSn") long userSn);
}
