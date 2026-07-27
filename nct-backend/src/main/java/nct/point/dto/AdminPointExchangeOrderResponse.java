package nct.point.dto;

import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;
import nct.point.domain.PointExchangeOrder;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 환전 - 관리자 처리 대기 행 응답 DTO] (F-PAY-012)
 * - GET /api/admin/point/exchange/orders 응답 본문의 배열 원소
 * - 관리자가 "누구에게(이름), 어디로(계좌), 얼마를(금액) 보내야 하나"를 한 줄에서 보게 한다
 */
@Getter
@Builder
public class AdminPointExchangeOrderResponse {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final String date;

    /** 신청자 (이름 + 회원번호 — 동명이인 구분용) */
    private final String userName;
    private final Long userSn;

    private final long amount;

    /** 신청 시점 계좌 스냅샷 — 관리자는 반드시 이 계좌로 이체한다 (현재 등록 계좌가 아님) */
    private final String bankName;
    private final String accountNo;

    private final String status;

    /** 도메인 모델 → 응답 DTO 변환 */
    public static AdminPointExchangeOrderResponse from(PointExchangeOrder o) {
        return AdminPointExchangeOrderResponse.builder()
                .id(o.getPtExcOrdSn())
                .date(o.getPtExcOrdRegDt() != null ? o.getPtExcOrdRegDt().format(DATE_FMT) : null)
                .userName(o.getUsrNm())
                .userSn(o.getUsrSn())
                .amount(o.getPtExcOrdAmt())
                .bankName(o.getPtExcOrdBankNm())
                .accountNo(o.getPtExcOrdAcntNo())
                .status(o.getStatusNm())
                .build();
    }
}
