package nct.product.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * [상품 조회수 중복 방지 이력 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/product/ProductViewLogMapper.xml
 */
@Mapper
public interface ProductViewLogMapper {

    /** 동일 상품·동일 방문자가 since 이후(24시간 내) 이미 방문 기록이 있는지 확인 */
    boolean existsRecentView(@Param("prdSn") Long prdSn, @Param("visitorKey") String visitorKey,
                              @Param("since") LocalDateTime since);

    /** 방문 이력 기록 */
    void insert(@Param("prdSn") Long prdSn, @Param("visitorKey") String visitorKey);
}
