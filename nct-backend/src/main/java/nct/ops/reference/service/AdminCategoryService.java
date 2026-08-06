package nct.ops.reference.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.Category;
import nct.ops.reference.dto.AdminCategoryOrderRequest;
import nct.ops.reference.dto.AdminCategoryReorderRequest;
import nct.ops.reference.dto.AdminCategoryRequest;
import nct.ops.reference.dto.AdminCategoryResponse;
import nct.ops.reference.mapper.CategoryMapper;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.reference.port.CategoryChangeHistoryPort;
import nct.servicerequest.service.ServiceRequestFormManagementService;

/**
 * 담당자 7 · F-COM-003: 관리자 카테고리 변경 규칙을 한곳에서 처리한다.
 * 관리자가 사유를 반복 입력하지 않아도 작업명과 전후 요약을 감사로그에 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private static final String DOMAIN_GROUP = "CATG01";
    private static final String APPROVAL_GROUP = "CATG02";
    private static final String APPROVAL_METHOD = "CATC0004";
    private static final String PRODUCT_DOMAIN = "CATC0001";
    private static final String SERVICE_DOMAIN = "CATC0002";
    private static final BigDecimal PRODUCT_SORT_START = BigDecimal.ONE;
    private static final BigDecimal SERVICE_SORT_START = BigDecimal.valueOf(11);

    private final CategoryMapper categoryMapper;
    private final ReferenceDataService referenceDataService;
    private final CategoryChangeHistoryPort changeHistoryPort;
    private final ServiceRequestFormManagementService formManagementService;

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
        BigDecimal maxSortNo = categoryMapper.findMaxChildSortNoByDomain(domainCode);
        BigDecimal nextSortNo = maxSortNo == null ? firstSortNo(domainCode) : maxSortNo.add(BigDecimal.ONE);
        Category category = toCategory(domainCode, request, nextSortNo);
        if (SERVICE_DOMAIN.equals(domainCode)) {
            category.setUseYn("N");
        }
        category.setName(name);
        category.setParentSn(parentSn);
        if (categoryMapper.insert(category, actor(actorUserId)) != 1 || category.getCategorySn() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        audit("CREATE", actorUserId, category,
                auditReason(request.changeReason(), "카테고리 등록"), null);
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
        Category updated = toCategory(domainCode, request, before.getSortNo());
        updated.setCategorySn(categorySn);
        updated.setParentSn(before.getParentSn());
        updated.setName(name);
        if (SERVICE_DOMAIN.equals(domainCode)
                && "N".equals(before.getUseYn())
                && "Y".equals(updated.getUseYn())
                && !formManagementService.hasActiveForm(categorySn)) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "서비스 요청 폼을 발행한 뒤 카테고리를 노출할 수 있습니다.");
        }
        if (same(before, updated)) return AdminCategoryResponse.from(before);
        if (categoryMapper.update(updated, actor(actorUserId)) != 1) throw new CustomException(ErrorCode.CONFLICT);
        audit("UPDATE", actorUserId, updated,
                auditReason(request.changeReason(), "카테고리 수정"), summary(before));
        return AdminCategoryResponse.from(updated);
    }

    /**
     * 관리자 목록의 한 칸 이동을 도메인 단위 트랜잭션으로 처리한다.
     * 기존 중복·간격 순서도 현재 화면 순서를 기준으로 연속 번호로 함께 정리한다.
     */
    @Transactional
    public List<AdminCategoryResponse> moveCategory(String domainCode, Long categorySn,
                                                    AdminCategoryOrderRequest request,
                                                    Long actorUserId) {
        validateActor(actorUserId);
        validateDomain(domainCode);
        if (categorySn == null || categorySn <= 0 || request == null
                || !("UP".equals(request.direction()) || "DOWN".equals(request.direction()))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        lockDomain(domainCode);
        List<Category> categories = new ArrayList<>(
                categoryMapper.findAllChildrenByDomainForUpdate(domainCode));
        int currentIndex = -1;
        for (int index = 0; index < categories.size(); index++) {
            if (categorySn.equals(categories.get(index).getCategorySn())) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        int destinationIndex = "UP".equals(request.direction())
                ? currentIndex - 1
                : currentIndex + 1;
        if (destinationIndex < 0 || destinationIndex >= categories.size()) {
            return categories.stream().map(AdminCategoryResponse::from).toList();
        }

        Collections.swap(categories, currentIndex, destinationIndex);
        return persistOrder(domainCode, categories, actorUserId);
    }

    /** 드래그로 전달된 전체 순서가 현재 도메인 목록과 정확히 일치할 때만 한 번에 저장한다. */
    @Transactional
    public List<AdminCategoryResponse> reorderCategories(String domainCode,
                                                         AdminCategoryReorderRequest request,
                                                         Long actorUserId) {
        validateActor(actorUserId);
        validateDomain(domainCode);
        if (request == null || request.categorySnOrder() == null
                || request.categorySnOrder().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        lockDomain(domainCode);
        List<Category> storedCategories = categoryMapper.findAllChildrenByDomainForUpdate(domainCode);
        List<Long> requestedOrder = request.categorySnOrder();
        Set<Long> uniqueIds = new HashSet<>(requestedOrder);
        if (requestedOrder.size() != storedCategories.size()
                || uniqueIds.size() != requestedOrder.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<Long, Category> categoriesById = new HashMap<>();
        for (Category category : storedCategories) {
            categoriesById.put(category.getCategorySn(), category);
        }
        List<Category> reordered = new ArrayList<>(requestedOrder.size());
        for (Long categorySn : requestedOrder) {
            Category category = categoriesById.get(categorySn);
            if (category == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            reordered.add(category);
        }
        return persistOrder(domainCode, reordered, actorUserId);
    }

    private List<AdminCategoryResponse> persistOrder(String domainCode,
                                                     List<Category> categories,
                                                     Long actorUserId) {
        BigDecimal nextSortNo = firstSortNo(domainCode);
        for (Category category : categories) {
            BigDecimal desiredSortNo = nextSortNo;
            nextSortNo = nextSortNo.add(BigDecimal.ONE);
            if (category.getSortNo().compareTo(desiredSortNo) == 0) {
                continue;
            }
            String beforeSummary = summary(category);
            category.setSortNo(desiredSortNo);
            if (categoryMapper.updateSortNo(category.getCategorySn(), domainCode,
                    desiredSortNo, actor(actorUserId)) != 1) {
                throw new CustomException(ErrorCode.CONFLICT);
            }
            audit("REORDER", actorUserId, category,
                    "카테고리 표시 순서 변경", beforeSummary);
        }
        return categories.stream().map(AdminCategoryResponse::from).toList();
    }

    private void validate(String domainCode, AdminCategoryRequest request, Long actorUserId) {
        validateActor(actorUserId);
        validateDomain(domainCode);
        referenceDataService.requireActiveCode(APPROVAL_GROUP, APPROVAL_METHOD);
        if (request == null || request.name() == null || request.name().trim().isEmpty()
                || request.name().trim().length() > 100
                || request.professional() == null || request.active() == null
                || (request.changeReason() != null && request.changeReason().length() > 500)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateActor(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String auditReason(String reason, String defaultReason) {
        return reason == null || reason.isBlank()
                ? defaultReason
                : defaultReason + ": " + reason.trim();
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

    private Category toCategory(String domainCode, AdminCategoryRequest request, BigDecimal sortNo) {
        Category category = new Category();
        category.setDomainCode(domainCode);
        category.setApprovalMethodCode(APPROVAL_METHOD);
        category.setSortNo(sortNo);
        category.setProfessionalYn(SERVICE_DOMAIN.equals(domainCode) ? "Y" : "N");
        category.setUseYn(Boolean.TRUE.equals(request.active()) ? "Y" : "N");
        return category;
    }

    private BigDecimal firstSortNo(String domainCode) {
        return PRODUCT_DOMAIN.equals(domainCode) ? PRODUCT_SORT_START : SERVICE_SORT_START;
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
