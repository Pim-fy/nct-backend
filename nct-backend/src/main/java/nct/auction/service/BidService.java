package nct.auction.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.MyBidHistoryItem;
import nct.auction.mapper.BidMapper;

@Service
@RequiredArgsConstructor
public class BidService {

    private final BidMapper bidMapper;

    @Transactional(readOnly = true)
    public List<MyBidHistoryItem> getMyBidHistory(Long usrSn) {
        return bidMapper.findMyBidHistory(usrSn);
    }
}
