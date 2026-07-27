package nct.settlement.dto;

import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;
import nct.settlement.domain.Settlement;
import nct.settlement.domain.SettlementStatus;

/**
 * [정산 - 목록 응답 DTO]
 * - GET /api/settlement 응답 본문의 배열 원소
 * - 상태 한글명은 CMM_CODE 조인 없이 SettlementStatus 3종을 코드에서 직접 매핑한다
 *   (포인트분류처럼 값이 늘어날 여지가 없는 고정 상태 머신이라 DB 조인 없이도 안전)
 */
@Getter
@Builder
public class SettlementResponse {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final Long tradeId;
    private final long amount;
    private final String statusCd;
    private final String statusName;
    private final String regDate;

    /** 도메인 모델 → 응답 DTO 변환 */
    public static SettlementResponse from(Settlement s) {
        return SettlementResponse.builder()
                .id(s.getStlmSn())
                .tradeId(s.getTrdSn())
                .amount(s.getStlmAmt())
                .statusCd(s.getStlmStatusCd())
                .statusName(statusName(s.getStlmStatusCd()))
                .regDate(s.getStlmRegDt() != null ? s.getStlmRegDt().format(DATE_FMT) : null)
                .build();
    }

    private static String statusName(String code) {
        if (SettlementStatus.PENDING.getCode().equals(code)) return "대기";
        if (SettlementStatus.ON_HOLD.getCode().equals(code)) return "보류";
        if (SettlementStatus.COMPLETED.getCode().equals(code)) return "완료";
        return code;
    }
}
