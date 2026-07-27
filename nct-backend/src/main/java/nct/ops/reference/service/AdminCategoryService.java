package nct.ops.reference.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.Category;
import nct.ops.reference.dto.AdminCategoryRequest;
import nct.ops.reference.dto.AdminCategoryResponse;
import nct.ops.reference.mapper.CategoryMapper;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.reference.port.CategoryChangeHistoryPort;

/**
 * 담당자 7 · F-COM-003: 관리자 카테고리 변경 규칙을 한곳에서 처리한다.
 * 담당자 6의 공용 감사 저장 계약 전까지 변경 사유와 전후 요약은 안전한 로그로 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private static final String DOMAIN_GROUP = "CATG01";
    private static final String APPROVAL_GROUP = "CATG02";
    private static final String APPROVAL_METHOD = "CATC0004";

    private final CategoryMapper categoryMapper;
    private final ReferenceDataService referenceDataService;
    private final CategoryChangeHistoryPort changeHistoryPort;

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> getCategories(String domainCode) {
        validateDomain(domainCode);
        return categoryMapper.findAllChildrenByDomain(domainCode).stream()
                .map(AdminCategoryResponse::from).toList();
    }

    @Transactional
    public AdminCategoryResponse createCategory(String domainCode, AdminCategoryRequest request,
                                                Long actorUserId) {
        validate(domainCode, request, actorUserId);
        Long parentSn = lockDomain(domainCode);
        String name = request.name().trim();
        rejectDuplicate(domainCode, name, null);
        Category category = toCategory(domainCode, request);
        category.setName(name);
        category.setParentSn(parentSn);
        if (categoryMapper.insert(category, actor(actorUserId)) != 1 || category.getCategorySn() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        audit("CREATE", actorUserId, category, request.changeReason(), null);
        return AdminCategoryResponse.from(category);
    }

    @Transactional
    public AdminCategoryResponse updateCategory(String domainCode, Long categorySn,
                                                AdminCategoryRequest request, Long actorUserId) {
        validate(domainCode, request, actorUserId);
        if (categorySn == null || categorySn <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        lockDomain(domainCode);
        Category before = categoryMapper.findChildByIdAndDomainForUpdate(categorySn, domainCode)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        String name = request.name().trim();
        rejectDuplicate(domainCode, name, categorySn);
        Category updated = toCategory(domainCode, request);
        updated.setCategorySn(categorySn);
        updated.setParentSn(before.getParentSn());
        updated.setName(name);
        if (same(before, updated)) return AdminCategoryResponse.from(before);
        if (categoryMapper.update(updated, actor(actorUserId)) != 1) throw new CustomException(ErrorCode.CONFLICT);
        audit("UPDATE", actorUserId, updated, request.changeReason(), summary(before));
        return AdminCategoryResponse.from(updated);
    }

    private void validate(String domainCode, AdminCategoryRequest request, Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) throw new CustomException(ErrorCode.UNAUTHORIZED);
        validateDomain(domainCode);
        referenceDataService.requireActiveCode(APPROVAL_GROUP, APPROVAL_METHOD);
        if (request == null || request.name() == null || request.name().trim().isEmpty()
                || request.name().trim().length() > 100 || request.sortNo() == null
                || request.sortNo() < 1 || request.sortNo() > 9999
                || request.professional() == null || request.active() == null
                || request.changeReason() == null || request.changeReason().trim().isEmpty()
                || request.changeReason().length() > 500) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateDomain(String domainCode) {
        referenceDataService.requireActiveCode(DOMAIN_GROUP, domainCode);
    }

    private void rejectDuplicate(String domainCode, String name, Long excludedId) {
        if (categoryMapper.countByName(domainCode, name, excludedId) > 0) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
    }

    private Long lockDomain(String domainCode) {
        return categoryMapper.findRootByDomainForUpdate(domainCode)
                .orElseThrow(() -> new CustomException(ErrorCode.CONFLICT)).getCategorySn();
    }

    private Category toCategory(String domainCode, AdminCategoryRequest request) {
        Category category = new Category();
        category.setDomainCode(domainCode);
        category.setApprovalMethodCode(APPROVAL_METHOD);
        category.setSortNo(BigDecimal.valueOf(request.sortNo()));
        category.setProfessionalYn("CATC0002".equals(domainCode)
                && Boolean.TRUE.equals(request.professional()) ? "Y" : "N");
        category.setUseYn(Boolean.TRUE.equals(request.active()) ? "Y" : "N");
        return category;
    }

    private boolean same(Category before, Category after) {
        return before.getName().equals(after.getName())
                && before.getSortNo().compareTo(after.getSortNo()) == 0
                && before.getProfessionalYn().equals(after.getProfessionalYn())
                && before.getUseYn().equals(after.getUseYn());
    }

    private void audit(String action, Long actorUserId, Category after, String reason, String before) {
        changeHistoryPort.record(new CategoryChangeHistoryCommand(
                action, actorUserId, after.getCategorySn(), reason, before, summary(after)));
    }

    private String summary(Category category) {
        return "domain=" + category.getDomainCode() + ",name=" + category.getName()
                + ",sort=" + category.getSortNo() + ",professional=" + category.getProfessionalYn()
                + ",use=" + category.getUseYn();
    }

    private String actor(Long actorUserId) {
        return "USR:" + actorUserId;
    }
}
