package nct.auction.port;

import nct.auction.dto.AuctionReportReference;

/** 담당자 7 · F-OPS-007: 공개 여부와 무관하게 신고 대상 경매를 최소 정보로 검증합니다. */
public interface AuctionReportReferenceReader {

    AuctionReportReference findByAuctionId(Long auctionId);
}
