package nct.trade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.ServiceTradeDetailResponse;
import nct.trade.dto.ServiceTradeDetailSource;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.AdminServiceTradeDetailReader;

/** 담당자 7 · F-OPS-005: 거래 소유 경계 안에서 관리자용 서비스 거래 상세를 조립합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceTradeDetailReaderService implements AdminServiceTradeDetailReader {

    private final TradeMapper tradeMapper;

    @Override
    public ServiceTradeDetailResponse findByTradeId(long tradeId) {
        if (tradeId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "거래번호가 올바르지 않습니다.");
        }

        ServiceTradeDetailSource source = tradeMapper.findAdminServiceTradeDetail(tradeId);
        if (source == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "서비스 거래를 찾을 수 없습니다.");
        }

        return new ServiceTradeDetailAssembler().assembleForAdmin(
                source,
                tradeMapper.findServiceScheduleHistory(tradeId));
    }
}
