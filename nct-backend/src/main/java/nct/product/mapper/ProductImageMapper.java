// Claude Code 작성 (BJN, 2026-07-17)
package nct.product.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.product.domain.ProductImage;

/**
 * [상품 이미지 - MyBatis 매퍼] (담당자6, F-AUC-002 이미지 연계)
 * - SQL 본문은 resources/mapper/product/ProductImageMapper.xml
 */
@Mapper
public interface ProductImageMapper {

    /** 상품 등록 시 이미지 목록 일괄 추가 (첫 번째가 대표) */
    void insertAll(@Param("images") List<ProductImage> images);
}
