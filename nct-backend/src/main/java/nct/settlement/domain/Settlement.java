package nct.settlement.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * [정산 - 정산 행 모델]
 * - SETTLEMENT 한 행. 거래 완료 후 판매자/제공자가 받을 거래대금의 추적 단위
 */
@Data
public class Settlement {

    private Long stlmSn;
    private Long trdSn;
    /** 담당자 7 · 거래 상세 경로 구분용 거래유형(물건 TRDC0001 / 서비스 TRDC0002). */
    private String tradeTypeCode;
    /** 정산대상(판매자/제공자) 회원일련번호 */
    private Long usrSn;
    private long stlmAmt;

    /** 정산상태공통코드(STLG01: 대기/보류/완료) */
    private String stlmStatusCd;

    private LocalDateTime stlmRegDt;
    private LocalDateTime stlmUpdtDt;
}
