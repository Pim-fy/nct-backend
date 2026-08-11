package nct.ops.operation.dto;

import java.math.BigDecimal;

/** 담당자 7 · F-OPS-005: 원문 경로를 제외한 관리자용 분쟁 증빙 파일 정보입니다. */
public record AdminDisputeEvidenceFileResponse(
        Long fileSn,
        String originalName,
        String extension,
        BigDecimal sizeAmount) {
}
