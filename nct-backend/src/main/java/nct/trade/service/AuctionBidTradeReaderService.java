package nct.trade.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.AuctionBidTradeReference;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.AuctionBidTradeReader;

/** TRADE 기술 소유 경계 안에서 낙찰 입찰과 물건 거래의 연결을 일괄 조회한다. */
@Service
@RequiredArgsConstructor
public class AuctionBidTradeReaderService implements AuctionBidTradeReader {

    private final TradeMapper tradeMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AuctionBidTradeReference> findByBuyerAndBidSns(
            long buyerUserId,
            Collection<Long> bidSns) {
        if (buyerUserId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "구매자 회원번호가 올바르지 않습니다.");
        }
        if (bidSns == null || bidSns.isEmpty()) {
            return List.of();
        }
        if (bidSns.stream().anyMatch(bidSn -> bidSn == null || bidSn <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "입찰 번호는 양수여야 합니다.");
        }

        return tradeMapper.findAuctionBidTradeReferencesByBuyerAndBidSns(buyerUserId, bidSns);
    }
}
