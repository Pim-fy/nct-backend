package nct.ops.reference.dto;

import nct.ops.reference.domain.Category;

/** 담당자 7 · F-COM-003: 관리자 화면에서 사용 중지 항목까지 표시하는 응답이다. */
public record AdminCategoryResponse(Long categorySn, String domainCode, String name,
                                    int sortNo, boolean professional, boolean active) {
    public static AdminCategoryResponse from(Category category) {
        return new AdminCategoryResponse(category.getCategorySn(), category.getDomainCode(),
                category.getName(), category.getSortNo().intValue(),
                "Y".equals(category.getProfessionalYn()), "Y".equals(category.getUseYn()));
    }
}
