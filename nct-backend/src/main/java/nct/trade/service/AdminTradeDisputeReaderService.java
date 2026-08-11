package nct.trade.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.trade.dto.AdminTradeDisputeQuery;
import nct.trade.dto.AdminTradeDisputeRecord;
import nct.trade.dto.TradeDisputeEvidenceFile;
import nct.trade.mapper.AdminTradeDisputeReadMapper;
import nct.trade.port.AdminTradeDisputeReader;

/** 담당자 7 · F-OPS-005: 거래 소유 경계 안에서 분쟁 조회 포트를 구현합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTradeDisputeReaderService implements AdminTradeDisputeReader {

    private final AdminTradeDisputeReadMapper mapper;

    @Override
    public long count(AdminTradeDisputeQuery query) {
        return mapper.count(query);
    }

    @Override
    public List<AdminTradeDisputeRecord> findPage(AdminTradeDisputeQuery query) {
        return List.copyOf(mapper.findPage(query));
    }

    @Override
    public AdminTradeDisputeRecord findById(long disputeSn) {
        return mapper.findById(disputeSn);
    }

    @Override
    public List<TradeDisputeEvidenceFile> findEvidenceFiles(long disputeSn) {
        return List.copyOf(mapper.findEvidenceFiles(disputeSn));
    }

    @Override
    public boolean hasEvidenceFile(long disputeSn, long fileSn) {
        return mapper.countEvidenceFileLink(disputeSn, fileSn) > 0;
    }
}
