package nct.ops.operation.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.operation.dto.AdminDisputeDetailResponse;
import nct.ops.operation.dto.AdminDisputeListItemResponse;
import nct.ops.operation.dto.AdminDisputeListRequest;
import nct.ops.operation.dto.AdminDisputePageResponse;
import nct.ops.reference.domain.CommonCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.settlement.domain.Settlement;
import nct.settlement.domain.SettlementStatus;
import nct.settlement.exception.SettlementException;
import nct.settlement.service.SettlementService;
import nct.trade.dto.AdminTradeDisputeQuery;
import nct.trade.dto.AdminTradeDisputeRecord;
import nct.trade.port.AdminTradeDisputeReader;

/**
 * 담당자 7 · F-OPS-005: 거래 분쟁, 거래 상태와 정산 조회 계약을 조립하는 관리자 읽기 서비스입니다.
 * 분쟁 판정이나 정산 상태 변경은 수행하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminDisputeQueryService {

    private static final String DISPUTE_TYPE_GROUP = "TRDG04";
    private static final String DISPUTE_STATUS_GROUP = "TRDG05";
    private static final String TRADE_TYPE_GROUP = "TRDG01";
    private static final String TRADE_STATUS_GROUP = "TRDG02";
    private static final String SETTLEMENT_STATUS_GROUP = "STLG01";
    private static final int MAX_PAGE_SIZE = 50;

    private final AdminTradeDisputeReader disputeReader;
    private final SettlementService settlementService;
    private final ReferenceDataService referenceDataService;

    @Transactional(readOnly = true)
    public AdminDisputePageResponse getPage(AdminDisputeListRequest request) {
        AdminTradeDisputeQuery query = toValidatedQuery(request);
        long totalItems = disputeReader.count(query);
        if (totalItems == 0) {
            return pageResponse(query, List.of(), 0);
        }

        CodeNames codeNames = loadCodeNames();
        List<AdminDisputeListItemResponse> items = disputeReader.findPage(query).stream()
                .map(record -> toListItem(record, codeNames))
                .toList();
        return pageResponse(query, items, totalItems);
    }

    @Transactional(readOnly = true)
    public AdminDisputeDetailResponse getDetail(Long disputeSn) {
        if (disputeSn == null || disputeSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AdminTradeDisputeRecord record = disputeReader.findById(disputeSn);
        if (record == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "존재하지 않는 거래 분쟁입니다.");
        }

        CodeNames names = loadCodeNames();
        SettlementSnapshot settlement = getSettlement(record.getTradeSn(), names.settlementStatuses());
        return AdminDisputeDetailResponse.builder()
                .disputeSn(record.getDisputeSn())
                .tradeSn(record.getTradeSn())
                .disputerUserSn(record.getDisputerUserSn())
                .disputeTypeCode(record.getDisputeTypeCode())
                .disputeTypeName(names.nameOf(names.disputeTypes(), record.getDisputeTypeCode()))
                .disputeStatusCode(record.getDisputeStatusCode())
                .disputeStatusName(names.nameOf(names.disputeStatuses(), record.getDisputeStatusCode()))
                .tradeTypeCode(record.getTradeTypeCode())
                .tradeTypeName(names.nameOf(names.tradeTypes(), record.getTradeTypeCode()))
                .tradeStatusCode(record.getTradeStatusCode())
                .tradeStatusName(names.nameOf(names.tradeStatuses(), record.getTradeStatusCode()))
                .sellerUserSn(record.getSellerUserSn())
                .buyerUserSn(record.getBuyerUserSn())
                .requesterUserSn(record.getRequesterUserSn())
                .providerUserSn(record.getProviderUserSn())
                .productSn(record.getProductSn())
                .serviceRequestSn(record.getServiceRequestSn())
                .settlementSn(settlement.settlementSn())
                .settlementStatusCode(settlement.statusCode())
                .settlementStatusName(settlement.statusName())
                .settlementOnHold(settlement.onHold())
                .registeredAt(record.getRegisteredAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private AdminDisputeListItemResponse toListItem(
            AdminTradeDisputeRecord record,
            CodeNames names) {
        SettlementSnapshot settlement = getSettlement(record.getTradeSn(), names.settlementStatuses());
        return AdminDisputeListItemResponse.builder()
                .disputeSn(record.getDisputeSn())
                .tradeSn(record.getTradeSn())
                .disputerUserSn(record.getDisputerUserSn())
                .disputeTypeCode(record.getDisputeTypeCode())
                .disputeTypeName(names.nameOf(names.disputeTypes(), record.getDisputeTypeCode()))
                .disputeStatusCode(record.getDisputeStatusCode())
                .disputeStatusName(names.nameOf(names.disputeStatuses(), record.getDisputeStatusCode()))
                .tradeTypeCode(record.getTradeTypeCode())
                .tradeTypeName(names.nameOf(names.tradeTypes(), record.getTradeTypeCode()))
                .tradeStatusCode(record.getTradeStatusCode())
                .tradeStatusName(names.nameOf(names.tradeStatuses(), record.getTradeStatusCode()))
                .settlementSn(settlement.settlementSn())
                .settlementStatusCode(settlement.statusCode())
                .settlementStatusName(settlement.statusName())
                .settlementOnHold(settlement.onHold())
                .registeredAt(record.getRegisteredAt())
                .build();
    }

    private SettlementSnapshot getSettlement(Long tradeSn, Map<String, String> settlementStatusNames) {
        try {
            Settlement settlement = settlementService.getSettlementByTrade(tradeSn);
            String code = settlement.getStlmStatusCd();
            return new SettlementSnapshot(
                    settlement.getStlmSn(),
                    code,
                    settlementStatusNames.getOrDefault(code, code),
                    SettlementStatus.ON_HOLD.getCode().equals(code));
        } catch (SettlementException exception) {
            if (exception.getErrorCode() == ErrorCode.SETTLEMENT_NOT_FOUND) {
                return SettlementSnapshot.none();
            }
            throw exception;
        }
    }

    private AdminTradeDisputeQuery toValidatedQuery(AdminDisputeListRequest request) {
        AdminDisputeListRequest source = request == null ? new AdminDisputeListRequest() : request;
        if (source.getPage() < 1 || source.getSize() < 1 || source.getSize() > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "페이지는 1 이상, 페이지 크기는 1~50이어야 합니다.");
        }

        String keyword = trimToNull(source.getKeyword());
        String typeCode = trimToNull(source.getDisputeTypeCode());
        String statusCode = trimToNull(source.getDisputeStatusCode());
        Long searchNumber = parseSearchNumber(keyword);
        if (typeCode != null) {
            referenceDataService.requireActiveCode(DISPUTE_TYPE_GROUP, typeCode);
        }
        if (statusCode != null) {
            referenceDataService.requireActiveCode(DISPUTE_STATUS_GROUP, statusCode);
        }

        AdminTradeDisputeQuery query = new AdminTradeDisputeQuery();
        query.setSearchNumber(searchNumber);
        query.setDisputeTypeCode(typeCode);
        query.setDisputeStatusCode(statusCode);
        query.setPage(source.getPage());
        query.setSize(source.getSize());
        return query;
    }

    private AdminDisputePageResponse pageResponse(
            AdminTradeDisputeQuery query,
            List<AdminDisputeListItemResponse> items,
            long totalItems) {
        return AdminDisputePageResponse.builder()
                .items(items)
                .page(query.getPage())
                .size(query.getSize())
                .totalItems(totalItems)
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / query.getSize()))
                .build();
    }

    private CodeNames loadCodeNames() {
        return new CodeNames(
                toNameMap(referenceDataService.getActiveCodes(DISPUTE_TYPE_GROUP)),
                toNameMap(referenceDataService.getActiveCodes(DISPUTE_STATUS_GROUP)),
                toNameMap(referenceDataService.getActiveCodes(TRADE_TYPE_GROUP)),
                toNameMap(referenceDataService.getActiveCodes(TRADE_STATUS_GROUP)),
                toNameMap(referenceDataService.getActiveCodes(SETTLEMENT_STATUS_GROUP)));
    }

    private Map<String, String> toNameMap(List<CommonCode> codes) {
        return codes.stream().collect(Collectors.toUnmodifiableMap(
                CommonCode::getCode,
                CommonCode::getName,
                (first, ignored) -> first));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long parseSearchNumber(String keyword) {
        if (keyword == null) {
            return null;
        }
        try {
            long value = Long.parseLong(keyword);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "검색어에는 양의 분쟁·거래·회원 번호만 입력할 수 있습니다.");
        }
    }

    private record SettlementSnapshot(
            Long settlementSn,
            String statusCode,
            String statusName,
            boolean onHold) {

        private static SettlementSnapshot none() {
            return new SettlementSnapshot(null, null, null, false);
        }
    }

    private record CodeNames(
            Map<String, String> disputeTypes,
            Map<String, String> disputeStatuses,
            Map<String, String> tradeTypes,
            Map<String, String> tradeStatuses,
            Map<String, String> settlementStatuses) {

        private String nameOf(Map<String, String> names, String code) {
            return code == null ? null : names.getOrDefault(code, code);
        }
    }
}
