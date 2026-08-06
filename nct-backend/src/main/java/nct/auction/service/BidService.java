package nct.auction.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.MyBidHistoryItem;
import nct.auction.mapper.BidMapper;
import nct.trade.dto.AuctionBidTradeReference;
import nct.trade.port.AuctionBidTradeReader;

@Service
@RequiredArgsConstructor
public class BidService {

    private final BidMapper bidMapper;
    private final AuctionBidTradeReader auctionBidTradeReader;

    @Transactional(readOnly = true)
    public List<MyBidHistoryItem> getMyBidHistory(Long usrSn) {
        List<MyBidHistoryItem> history = bidMapper.findMyBidHistory(usrSn);
        List<Long> wonBidSns = history.stream()
                .filter(item -> "WON".equals(item.resolveDisplayStatus()))
                .map(MyBidHistoryItem::getBidSn)
                .toList();

        if (wonBidSns.isEmpty()) {
            return history;
        }

        Map<Long, AuctionBidTradeReference> tradeByBidSn = auctionBidTradeReader
                .findByBuyerAndBidSns(usrSn, wonBidSns)
                .stream()
                .collect(Collectors.toMap(
                        AuctionBidTradeReference::getBidSn,
                        Function.identity(),
                        (existing, ignored) -> existing));

        history.forEach(item -> {
            AuctionBidTradeReference trade = tradeByBidSn.get(item.getBidSn());
            if (trade != null) {
                item.setTradeId(trade.getTradeId());
            }
        });
        return history;
    }
}
