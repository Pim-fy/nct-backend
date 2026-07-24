// Claude Code 작성 (BJN, 2026-07-17)
package nct.product.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.product.domain.ProductImage;
import nct.product.dto.ProductImageItem;

/**
 * [상품 이미지 - MyBatis 매퍼] (담당자6, F-AUC-002 이미지 연계)
 * - SQL 본문은 resources/mapper/product/ProductImageMapper.xml
 */
@Mapper
public interface ProductImageMapper {

    /** 상품 등록 시 이미지 목록 일괄 추가 (첫 번째가 대표) */
    void insertAll(@Param("images") List<ProductImage> images);

    /** 상품 이미지 전체 삭제 — 수정 시 재삽입 전 호출 */
    void deleteByPrdSn(@Param("prdSn") Long prdSn);

    /** 상품 이미지 목록 조회 — 상세 조회 시 임시저장 복원용 */
    List<ProductImageItem> findImagesByPrdSn(@Param("prdSn") Long prdSn);
}
