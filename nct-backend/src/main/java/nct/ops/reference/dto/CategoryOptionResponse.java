package nct.ops.reference.dto;

import java.math.BigDecimal;

import nct.ops.reference.domain.Category;

/**
 * 담당자 7 · F-COM-003: 상품 등록과 서비스 탐색 화면에 전달하는 카테고리 선택지다.
 * 기존 담당자 2 프론트가 사용하는 필드명을 유지해 별도 변환 없이 연결된다.
 */
public record CategoryOptionResponse(Long catSn, Long catParentSn, String domainCd,
                                     String catNm, BigDecimal sortNo,
                                     boolean professional) {
    public static CategoryOptionResponse from(Category category) {
        return new CategoryOptionResponse(category.getCategorySn(), category.getParentSn(),
                category.getDomainCode(), category.getName(), category.getSortNo(),
                "Y".equals(category.getProfessionalYn()));
    }
}
