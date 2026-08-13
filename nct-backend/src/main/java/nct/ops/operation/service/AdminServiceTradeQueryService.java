package nct.ops.operation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.security.service.SensitiveDataMasker;
import nct.trade.dto.ServiceScheduleHistoryItem;
import nct.trade.dto.ServiceTradeDetailResponse;
import nct.trade.port.AdminServiceTradeDetailReader;

/** 담당자 7 · F-OPS-005: 관리자 화면에 노출할 서비스 거래 상세를 마스킹해 반환합니다. */
@Service
@RequiredArgsConstructor
public class AdminServiceTradeQueryService {

    private final AdminServiceTradeDetailReader tradeDetailReader;
    private final SensitiveDataMasker sensitiveDataMasker;

    @Transactional(readOnly = true)
    public ServiceTradeDetailResponse getDetail(Long tradeId) {
        if (tradeId == null || tradeId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "거래번호가 올바르지 않습니다.");
        }

        ServiceTradeDetailResponse source = tradeDetailReader.findByTradeId(tradeId);
        List<ServiceScheduleHistoryItem> maskedHistory = source.scheduleHistory().stream()
                .map(item -> new ServiceScheduleHistoryItem(
                        item.id(),
                        item.eventType(),
                        item.occurredAt(),
                        item.requestedScheduleAt(),
                        sensitiveDataMasker.maskText(item.reason()),
                        item.actorRole()))
                .toList();

        return new ServiceTradeDetailResponse(
                source.tradeId(),
                source.serviceRequestId(),
                "ADMIN",
                source.tradeStatusCode(),
                source.tradeAmount(),
                source.autoCompleteAt(),
                sensitiveDataMasker.maskText(source.serviceRequestTitle()),
                sensitiveDataMasker.maskText(source.quoteSummary()),
                source.scheduleLabel(),
                null,
                source.escrowStatusCode(),
                source.escrowStatusLabel(),
                source.chatRoomStatus(),
                false,
                maskedHistory,
                List.of(),
                null,
                null,
                null,
                null,
                0,
                sensitiveDataMasker.maskText(source.serviceRequestContent()),
                0L);
    }
}
