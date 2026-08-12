package nct.abuse.dto;

import java.math.BigDecimal;

/** 담당자 7 · F-COM-018: 신고 상세에 노출할 보호 첨부파일 메타데이터입니다. */
public record AbuseReportFileResponse(
        Long fileSn,
        String originalName,
        String extension,
        BigDecimal sizeAmount,
        Integer sortNo) {
}
