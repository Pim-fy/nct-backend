package nct.ops.risk.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.risk.dto.AdminRiskEventListItemResponse;
import nct.ops.risk.dto.AdminRiskEventPageResponse;
import nct.ops.risk.dto.AdminRiskEventTypeSummaryResponse;
import nct.ops.risk.mapper.RiskEventMapper;

/**
 * 담당자 7 · F-OPS-011 관리자 리스크 이벤트 조회·집계 서비스입니다.
 * 관리자 대시보드와 향후 리스크 목록 화면이 함께 사용하며, 이벤트를 수정하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminRiskEventService {

    private static final String RISK_EVENT_GROUP = "RSKG01";
    private static final int MAX_PAGE_SIZE = 50;

    private final RiskEventMapper riskEventMapper;
    private final ReferenceDataService referenceDataService;

    @Transactional(readOnly = true)
    public AdminRiskEventPageResponse getRiskEvents(String typeCode, String processed, int page, int size) {
        validatePage(page, size);
        String normalizedType = normalizeType(typeCode);
        String processedYn = normalizeProcessed(processed);
        long totalItems = riskEventMapper.countAdminRiskEvents(normalizedType, processedYn);
        List<AdminRiskEventListItemResponse> items = totalItems == 0 || (long) (page - 1) * size >= totalItems
                ? List.of()
                : riskEventMapper.findAdminRiskEvents(normalizedType, processedYn, (long) (page - 1) * size, size);
        return AdminRiskEventPageResponse.builder().items(items).page(page).size(size)
                .totalItems(totalItems).totalPages(totalItems == 0 ? 0 : (int) ((totalItems + size - 1) / size)).build();
    }

    @Transactional(readOnly = true)
    public List<AdminRiskEventTypeSummaryResponse> getTypeSummary(String processed) {
        return riskEventMapper.countAdminRiskEventsByType(normalizeProcessed(processed));
    }

    private String normalizeType(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) return null;
        String normalized = typeCode.trim();
        referenceDataService.requireActiveCode(RISK_EVENT_GROUP, normalized);
        return normalized;
    }

    private String normalizeProcessed(String processed) {
        if (processed == null || processed.isBlank()) return null;
        String normalized = processed.trim().toUpperCase();
        if (!"Y".equals(normalized) && !"N".equals(normalized)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
