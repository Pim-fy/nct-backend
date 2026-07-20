package nct.point.dto;

import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;
import nct.point.domain.PointExchangeOrder;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 환전 - 신청 이력 행 응답 DTO] (F-PAY-012)
 * - GET /api/point/exchange/orders 응답 본문의 배열 원소
 * - 충전 이력(PointChargeOrderResponse)과 같은 표시 계약 — 프론트가 같은 테이블 패턴으로 그린다
 */
@Getter
@Builder
public class PointExchangeOrderResponse {

    /** 화면 표시용 날짜 형식 (충전·원장 응답과 동일: "2026-07-17 14:00") */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final String date;
    private final long amount;

    /** 상태 한글명 (신청/완료/반려) — 프론트 배지 색 분기 키 */
    private final String status;
    private final String statusCd;

    /** 신청 시점 계좌 스냅샷 — 본인 확인용 표시 */
    private final String bankName;
    private final String accountNo;

    /** 반려 사유 (없으면 null → 화면에 '-') */
    private final String rejectReason;

    /** 처리(지급완료/반려) 일시 — 아직 처리 전이면 null */
    private final String processedDate;

    /** 도메인 모델 → 응답 DTO 변환 */
    public static PointExchangeOrderResponse from(PointExchangeOrder o) {
        return PointExchangeOrderResponse.builder()
                .id(o.getPtExcOrdSn())
                .date(o.getPtExcOrdRegDt() != null ? o.getPtExcOrdRegDt().format(DATE_FMT) : null)
                .amount(o.getPtExcOrdAmt())
                .status(o.getStatusNm())
                .statusCd(o.getPtExcOrdStatusCd())
                .bankName(o.getPtExcOrdBankNm())
                .accountNo(o.getPtExcOrdAcntNo())
                .rejectReason(o.getPtExcOrdRjctRsnCn())
                .processedDate(o.getPtExcOrdProcDt() != null ? o.getPtExcOrdProcDt().format(DATE_FMT) : null)
                .build();
    }
}
