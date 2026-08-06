package nct.ops.reference.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.reference.domain.Category;

/**
 * CATEGORY 테이블을 조회하는 MyBatis 연결부다.
 * SQL은 {@code resources/mapper/ops/reference/CategoryMapper.xml}에 있다.
 * Controller에서 직접 호출하지 않고 {@code ReferenceDataService}를 통해 사용한다.
 */
@Mapper
public interface CategoryMapper {

    /** 고유번호와 도메인이 모두 일치하는 활성 카테고리 한 건을 찾는다. */
    Optional<Category> findActiveByIdAndDomain(@Param("categorySn") Long categorySn,
                                               @Param("domainCode") String domainCode);

    /** 물건 또는 서비스 도메인에 속한 활성 카테고리 목록을 표시 순서대로 찾는다. */
    List<Category> findActiveByDomain(@Param("domainCode") String domainCode);

    /** 관리자 화면용: 사용 중지 항목까지 포함하되 선택 가능한 하위 카테고리만 조회한다. */
    List<Category> findAllChildrenByDomain(@Param("domainCode") String domainCode);

    /** 표시 순서 변경 중 같은 도메인의 하위 카테고리를 잠그고 현재 순서대로 조회한다. */
    List<Category> findAllChildrenByDomainForUpdate(@Param("domainCode") String domainCode);

    /** 신규 서비스 카테고리를 마지막에 추가하기 위한 현재 최대 표시 순서다. */
    BigDecimal findMaxChildSortNoByDomain(@Param("domainCode") String domainCode);

    Optional<Category> findRootByDomainForUpdate(@Param("domainCode") String domainCode);

    Optional<Category> findChildByIdAndDomainForUpdate(@Param("categorySn") Long categorySn,
                                                       @Param("domainCode") String domainCode);

    Optional<Category> findChildByIdAndDomain(@Param("categorySn") Long categorySn,
                                               @Param("domainCode") String domainCode);

    int countByName(@Param("domainCode") String domainCode,
                    @Param("name") String name,
                    @Param("excludeCategorySn") Long excludeCategorySn);

    int insert(@Param("category") Category category, @Param("actorId") String actorId);

    int update(@Param("category") Category category, @Param("actorId") String actorId);

    int updateSortNo(@Param("categorySn") Long categorySn,
                     @Param("domainCode") String domainCode,
                     @Param("sortNo") BigDecimal sortNo,
                     @Param("actorId") String actorId);

    int updateUseYn(@Param("categorySn") Long categorySn,
                    @Param("domainCode") String domainCode,
                    @Param("useYn") String useYn,
                    @Param("actorId") String actorId);
}
