package nct.auction.service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionIdByProduct;
import nct.auction.dto.AuctionReferenceTitle;
import nct.auction.mapper.AuctionMapper;
import nct.auction.port.AuctionReferenceTitleReader;

/** 담당자 7 · F-COM-018: 신고 목록용 경매 제목을 한 번의 배치 조회로 제공합니다. */
@Service
@RequiredArgsConstructor
public class AuctionReferenceTitleQueryService implements AuctionReferenceTitleReader {

    private static final int QUERY_BATCH_SIZE = 500;

    private final AuctionMapper auctionMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findTitles(Collection<Long> auctionIds) {
        if (auctionIds == null || auctionIds.isEmpty()) {
            return Map.of();
        }
        List<Long> normalizedIds = auctionIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> titles = new LinkedHashMap<>();
        for (int start = 0; start < normalizedIds.size(); start += QUERY_BATCH_SIZE) {
            int end = Math.min(start + QUERY_BATCH_SIZE, normalizedIds.size());
            for (AuctionReferenceTitle row : auctionMapper.findAuctionReferenceTitles(
                    normalizedIds.subList(start, end))) {
                if (row != null && row.getAuctionId() != null
                        && row.getTitle() != null && !row.getTitle().isBlank()) {
                    titles.putIfAbsent(row.getAuctionId(), row.getTitle().trim());
                }
            }
        }
        return Collections.unmodifiableMap(titles);
    }

    /** 담당자 7 · F-OPS-007: 신고에 연결된 물품 거래를 해당 경매 상세로 이동시키는 배치 계약입니다. */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> findAuctionIdsByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Long> normalizedIds = productIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> auctionIds = new LinkedHashMap<>();
        for (int start = 0; start < normalizedIds.size(); start += QUERY_BATCH_SIZE) {
            int end = Math.min(start + QUERY_BATCH_SIZE, normalizedIds.size());
            for (AuctionIdByProduct row : auctionMapper.findAuctionIdsByProductIds(
                    normalizedIds.subList(start, end))) {
                if (row != null && row.getProductId() != null && row.getAuctionId() != null) {
                    auctionIds.putIfAbsent(row.getProductId(), row.getAuctionId());
                }
            }
        }
        return Collections.unmodifiableMap(auctionIds);
    }
}
