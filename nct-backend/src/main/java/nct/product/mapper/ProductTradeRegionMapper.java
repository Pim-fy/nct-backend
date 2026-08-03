package nct.product.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.product.domain.ProductTradeRegion;
import nct.product.dto.ProductTradeRegionItem;

/**
 * [상품 희망 거래지역 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/product/ProductTradeRegionMapper.xml
 */
@Mapper
public interface ProductTradeRegionMapper {

    /** 상품 등록 시 희망 거래지역 목록 일괄 추가 (최대 5곳) */
    void insertAll(@Param("regions") List<ProductTradeRegion> regions);

    /** 희망 거래지역 전체 삭제 — 수정 시 재삽입 전 호출 */
    void deleteByPrdSn(@Param("prdSn") Long prdSn);

    /** 희망 거래지역 목록 조회 — 상세 조회 시 임시저장 복원용 */
    List<ProductTradeRegionItem> findByPrdSn(@Param("prdSn") Long prdSn);
}
