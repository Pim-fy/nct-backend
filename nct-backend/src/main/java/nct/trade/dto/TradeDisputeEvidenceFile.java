package nct.trade.dto;

import java.math.BigDecimal;

/** 담당자 7 · F-OPS-005: 관리자 분쟁 상세에 제공할 보호 파일 메타데이터입니다. */
public record TradeDisputeEvidenceFile(
        Long fileSn,
        String originalName,
        String extension,
        BigDecimal sizeAmount) {
}
