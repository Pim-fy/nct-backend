package nct.ops.reference.dto;

import nct.servicerequest.dto.ServiceRequestFormResponse;

/** 담당자 7 · F-COM-003/F-SVC-002: 카테고리와 편집 대상 폼 버전을 한 번에 반환한다. */
public record AdminServiceRequestFormEditorResponse(
        Long categorySn,
        String categoryName,
        boolean categoryActive,
        Integer activeVersion,
        boolean draft,
        ServiceRequestFormResponse form) {
}
