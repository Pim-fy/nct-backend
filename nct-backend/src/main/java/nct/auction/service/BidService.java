package nct.auction.service;

import java.util.List;

import org.springframework.stereotype.Service;

import nct.auction.dto.MyBidHistoryItem;
import nct.auction.mapper.BidMapper;

import lombok.RequiredArgsConstructor;

/**
 * [F-AUC-022 내 입찰 내역 조회]
 * - 09_기능단위_7인_업무분장 v10(CHG-018)부터 F-AUC-013/014/018/019(입찰 검증·실행·즉시구매)와
 *   AUCTION/BID 테이블 기술 소유권이 담당자5로 이관됐다. 이 서비스는 담당자3에게 남은
 *   F-AUC-022 하나만 담당하며, 예전에 있던 validateBid/executeBid/executeBuyNow 및 그
 *   검증 로직·포인트/거래 계약 호출은 전부 삭제했다 (담당자5가 새로 만들 예정).
 */
@Service
@RequiredArgsConstructor
public class BidService {

    private final BidMapper bidMapper;

    /**
     * - 단순 조회라 @Transactional, 잠금, 검증 규칙이 전혀 필요 없다.
     * - "본인 입찰 내역만 조회" 규칙은 usrSn 을 클라이언트 입력이 아니라 컨트롤러가
     *   @AuthenticationPrincipal 에서 꺼낸 로그인 사용자 번호로만 호출하는 것으로 지킨다
     *   (다른 사람의 usrSn 을 파라미터로 받는 API 자체를 만들지 않는다).
     */
    public List<MyBidHistoryItem> getMyBidHistory(Long usrSn) {
        return bidMapper.findMyBidHistory(usrSn);
    }
}
