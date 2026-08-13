package nct.auction.port;

import java.util.Collection;
import java.util.Map;

/** 담당자 7 · F-COM-018: 신고 참조 화면에 노출할 경매 상품 제목을 일괄 조회하는 계약입니다. */
public interface AuctionReferenceTitleReader {

    Map<Long, String> findTitles(Collection<Long> auctionIds);
}
