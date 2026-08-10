package nct.ops.reference.dto;

import nct.ops.reference.domain.Category;
import nct.servicerequest.dto.ServiceRequestFormVersionStatus;

/** 담당자 7 · F-COM-003: 관리자 화면에서 사용 중지 항목까지 표시하는 응답이다. */
public record AdminCategoryResponse(Long categorySn, String domainCode, String name,
                                    int sortNo, boolean professional, boolean active,
                                    Integer activeFormVersion, Integer draftFormVersion) {
    public AdminCategoryResponse(Long categorySn, String domainCode, String name,
                                 int sortNo, boolean professional, boolean active) {
        this(categorySn, domainCode, name, sortNo, professional, active, null, null);
    }

    public static AdminCategoryResponse from(Category category) {
        return from(category, null);
    }

    public static AdminCategoryResponse from(
            Category category,
            ServiceRequestFormVersionStatus formStatus) {
        return new AdminCategoryResponse(category.getCategorySn(), category.getDomainCode(),
                category.getName(), category.getSortNo().intValue(),
                "Y".equals(category.getProfessionalYn()), "Y".equals(category.getUseYn()),
                formStatus == null ? null : formStatus.getActiveVersion(),
                formStatus == null ? null : formStatus.getDraftVersion());
    }
}
