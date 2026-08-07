package nct.ops.servicequery.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.AdminCategoryService;
import nct.ops.servicequery.dto.AdminServiceRequestListRequest;
import nct.ops.servicequery.dto.AdminServiceRequestDetailResponse;
import nct.ops.servicequery.dto.AdminServiceRequestIntegratedStatus;
import nct.ops.servicequery.dto.AdminServiceRequestListItemResponse;
import nct.ops.servicequery.dto.AdminServiceRequestPageResponse;
import nct.ops.security.service.SensitiveDataMasker;
import nct.quote.dto.AdminQuoteSummary;
import nct.quote.port.AdminQuoteSummaryReader;
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
    private final AdminQuoteSummaryReader quoteSummaryReader;

    @Transactional(readOnly = true)
    public AdminServiceRequestPageResponse getPage(AdminServiceRequestListRequest request) {
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
        List<Long> serviceRequestIds = page.getItems().stream()
                .map(item -> item.getServiceRequestId())
                .toList();
        Map<Long, AdminQuoteSummary> quoteSummaries = quoteSummaryReader.findSummaries(serviceRequestIds);
        List<AdminServiceRequestListItemResponse> items = page.getItems().stream()
                .map(item -> {
                    AdminQuoteSummary quote = quoteSummaries.get(item.getServiceRequestId());
                    return AdminServiceRequestListItemResponse.from(
                            item,
                            quote,
                            integratedStatus(item.getStatusCode(), quote));
                })
                .toList();
        return new AdminServiceRequestPageResponse(
                items,
                page.getPage(),
                page.getSize(),
                page.getTotalItems(),
                page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminServiceRequestDetailResponse getDetail(Long serviceRequestId) {
        if (serviceRequestId == null || serviceRequestId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AdminServiceRequestDetail detail = reader.readDetail(serviceRequestId);
        detail.setTitle(sensitiveDataMasker.maskText(detail.getTitle()));
        detail.setContent(sensitiveDataMasker.maskText(detail.getContent()));
        AdminQuoteSummary quote = quoteSummaryReader.findSummaries(List.of(serviceRequestId))
                .get(serviceRequestId);
        return AdminServiceRequestDetailResponse.from(
                detail,
                quote,
                integratedStatus(detail.getStatusCode(), quote));
    }

    private AdminServiceRequestIntegratedStatus integratedStatus(
            String sourceStatusCode,
            AdminQuoteSummary quote) {
        if (sourceStatusCode == null) {
            throw new CustomException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "서비스 요청 상태가 없습니다.");
        }
        int activeQuoteCount = quote == null ? 0 : quote.getActiveQuoteCount();
        return switch (sourceStatusCode) {
            case "SVCC0001" -> new AdminServiceRequestIntegratedStatus("RECEIVED", "접수");
            case "SVCC0002" -> activeQuoteCount > 0
                    ? new AdminServiceRequestIntegratedStatus("IN_PROGRESS", "처리중")
                    : new AdminServiceRequestIntegratedStatus("RECEIVED", "접수");
            case "SVCC0003" -> new AdminServiceRequestIntegratedStatus("IN_PROGRESS", "처리중");
            case "SVCC0004" -> new AdminServiceRequestIntegratedStatus("COMPLETED", "완료");
            default -> throw new CustomException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "알 수 없는 서비스 요청 상태입니다: " + sourceStatusCode);
        };
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
