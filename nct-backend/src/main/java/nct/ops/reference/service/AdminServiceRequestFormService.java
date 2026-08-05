package nct.ops.reference.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.Category;
import nct.ops.reference.dto.AdminServiceRequestFormEditorResponse;
import nct.ops.reference.mapper.CategoryMapper;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.reference.port.CategoryChangeHistoryPort;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest;
import nct.servicerequest.dto.ServiceRequestFormResponse;
import nct.servicerequest.service.ServiceRequestFormManagementService;

/**
 * 담당자 7 · F-COM-003/F-SVC-002: 관리자 권한의 카테고리 폼 편집을 조정한다.
 * 서비스 카테고리 잠금, 폼 버전 저장·발행, 카테고리 노출 전환, 감사기록을 한 트랜잭션으로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceRequestFormService {

    private static final String SERVICE_DOMAIN = "CATC0002";
    private static final String YES = "Y";

    private final CategoryMapper categoryMapper;
    private final ServiceRequestFormManagementService formManagementService;
    private final CategoryChangeHistoryPort changeHistoryPort;

    @Transactional(readOnly = true)
    public AdminServiceRequestFormEditorResponse getEditor(Long categorySn) {
        validateId(categorySn);
        Category category = categoryMapper.findChildByIdAndDomain(categorySn, SERVICE_DOMAIN)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        ServiceRequestFormResponse form = formManagementService.getLatestForm(categorySn).orElse(null);
        return response(category, form);
    }

    @Transactional
    public AdminServiceRequestFormEditorResponse saveDraft(
            Long categorySn,
            AdminServiceRequestFormDraftRequest request,
            Long actorUserId) {
        validateActor(actorUserId);
        Category category = lockCategory(categorySn);
        ServiceRequestFormResponse form = formManagementService.saveDraft(
                categorySn, request, actor(actorUserId));
        record(
                "FORM_DRAFT",
                actorUserId,
                category,
                "서비스 요청 폼 초안 저장",
                "formVersion=" + form.getFormVersion() + ",steps=" + form.getSteps().size());
        return response(category, form);
    }

    @Transactional
    public AdminServiceRequestFormEditorResponse publish(
            Long categorySn,
            Long formTemplateSn,
            Long actorUserId) {
        validateActor(actorUserId);
        Category category = lockCategory(categorySn);
        String before = summary(category);
        ServiceRequestFormResponse form = formManagementService.publish(
                categorySn, formTemplateSn, actor(actorUserId));
        if (!YES.equals(category.getUseYn())) {
            if (categoryMapper.updateUseYn(
                    categorySn, SERVICE_DOMAIN, YES, actor(actorUserId)) != 1) {
                throw new CustomException(ErrorCode.CONFLICT);
            }
            category.setUseYn(YES);
        }
        changeHistoryPort.record(new CategoryChangeHistoryCommand(
                "FORM_PUBLISH",
                actorUserId,
                categorySn,
                "서비스 요청 폼 발행 v" + form.getFormVersion(),
                before,
                summary(category) + ",formVersion=" + form.getFormVersion()));
        return response(category, form);
    }

    private AdminServiceRequestFormEditorResponse response(
            Category category,
            ServiceRequestFormResponse form) {
        int activeVersion = formManagementService.getActiveVersion(category.getCategorySn());
        boolean draft = form != null && !YES.equals(form.getActiveYn());
        return new AdminServiceRequestFormEditorResponse(
                category.getCategorySn(),
                category.getName(),
                YES.equals(category.getUseYn()),
                activeVersion,
                draft,
                form);
    }

    private Category lockCategory(Long categorySn) {
        validateId(categorySn);
        return categoryMapper.findChildByIdAndDomainForUpdate(categorySn, SERVICE_DOMAIN)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private void record(
            String action,
            Long actorUserId,
            Category category,
            String reason,
            String after) {
        changeHistoryPort.record(new CategoryChangeHistoryCommand(
                action,
                actorUserId,
                category.getCategorySn(),
                reason,
                summary(category),
                summary(category) + "," + after));
    }

    private String summary(Category category) {
        return "domain=" + category.getDomainCode()
                + ",name=" + category.getName()
                + ",use=" + category.getUseYn();
    }

    private void validateId(Long value) {
        if (value == null || value <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateActor(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String actor(Long actorUserId) {
        return "USR:" + actorUserId;
    }
}
