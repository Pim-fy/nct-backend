package nct.auction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionReportReference;
import nct.auction.mapper.AuctionMapper;
import nct.auction.port.AuctionReportReferenceReader;

/** 담당자 7 · F-OPS-007: 종료·숨김 경매도 공개 상세 의존 없이 신고 검증 정보를 조회합니다. */
@Service
@RequiredArgsConstructor
public class AuctionReportReferenceQueryService implements AuctionReportReferenceReader {

    private final AuctionMapper auctionMapper;

    @Override
    @Transactional(readOnly = true)
    public AuctionReportReference findByAuctionId(Long auctionId) {
        return auctionMapper.findAuctionReportReference(auctionId);
    }
}
