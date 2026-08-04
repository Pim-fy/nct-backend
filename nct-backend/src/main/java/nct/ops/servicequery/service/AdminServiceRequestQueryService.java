package nct.ops.servicequery.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.AdminCategoryService;
import nct.ops.servicequery.dto.AdminServiceRequestListRequest;
import nct.ops.security.service.SensitiveDataMasker;
import nct.servicerequest.dto.AdminServiceRequestDetail;
import nct.servicerequest.dto.AdminServiceRequestPage;
import nct.servicerequest.dto.AdminServiceRequestSearchCondition;
import nct.servicerequest.port.AdminServiceRequestReader;

/**
 * 담당자 7 · 관리자 42 서비스 요청 관리.
 * 관리자 검색값을 검증한 뒤 서비스요청 도메인의 읽기 계약만 호출한다.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceRequestQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final String SERVICE_CATEGORY_DOMAIN = "CATC0002";

    private final AdminServiceRequestReader reader;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final AdminCategoryService adminCategoryService;

    @Transactional(readOnly = true)
    public AdminServiceRequestPage getPage(AdminServiceRequestListRequest request) {
        AdminServiceRequestListRequest normalized = request == null
                ? new AdminServiceRequestListRequest()
                : request;
        normalize(normalized);
        AdminServiceRequestPage page = reader.readPage(AdminServiceRequestSearchCondition.builder()
                .keyword(normalized.getKeyword())
                .categorySn(normalized.getCategorySn())
                .statusCode(normalized.getStatusCode())
                .registeredFrom(normalized.getRegisteredFrom())
                .registeredTo(normalized.getRegisteredTo())
                .page(normalized.getPage())
                .size(normalized.getSize())
                .build());
        page.getItems().forEach(item -> item.setTitle(sensitiveDataMasker.maskText(item.getTitle())));
        return page;
    }

    @Transactional(readOnly = true)
    public AdminServiceRequestDetail getDetail(Long serviceRequestId) {
        if (serviceRequestId == null || serviceRequestId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AdminServiceRequestDetail detail = reader.readDetail(serviceRequestId);
        detail.setTitle(sensitiveDataMasker.maskText(detail.getTitle()));
        detail.setContent(sensitiveDataMasker.maskText(detail.getContent()));
        return detail;
    }

    private void normalize(AdminServiceRequestListRequest request) {
        request.setPage(Math.max(1, request.getPage()));
        request.setSize(request.getSize() <= 0 ? DEFAULT_SIZE : Math.min(request.getSize(), MAX_SIZE));
        request.setKeyword(trimToNull(request.getKeyword()));
        request.setStatusCode(trimToNull(request.getStatusCode()));

        if (request.getKeyword() != null && request.getKeyword().length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.getCategorySn() != null && request.getCategorySn() <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.getCategorySn() != null && adminCategoryService
                .getCategories(SERVICE_CATEGORY_DOMAIN)
                .stream()
                .noneMatch(category -> category.categorySn().equals(request.getCategorySn()))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDate registeredFrom = request.getRegisteredFrom();
        LocalDate registeredTo = request.getRegisteredTo();
        if (registeredFrom != null && registeredTo != null && registeredFrom.isAfter(registeredTo)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
