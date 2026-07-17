package nct.point.dto;

import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;
import nct.point.domain.PointChargeOrder;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [포인트 충전 - 주문 이력 행 응답 DTO]
 * - GET /api/point/charge/orders 응답 본문의 배열 원소
 * - 원장(확정 충전)과 달리 실패·취소·대기 건까지 포함한 "시도 이력"이다
 *   (포인트_모듈_가이드라인 §9: 포인트 변동으로 이어지지 않은 실패 이벤트도 사용자에게 보여준다)
 */
@Getter
@Builder
public class PointChargeOrderResponse {

    /** 화면 표시용 날짜 형식 (원장 응답과 동일: "2026-07-16 14:00") */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final String date;

    /** 주문번호 — 문의 대응 시 사용자·PG 양쪽에서 추적하는 키라 화면에 노출한다 */
    private final String orderNo;

    private final long amount;

    /** 상태 한글명 (대기/완료/실패/취소) — 프론트 배지 색 분기 키 */
    private final String status;
    private final String statusCd;

    /** 실패·취소 사유 (없으면 null → 화면에 '-') */
    private final String failReason;

    /** 도메인 모델 → 응답 DTO 변환 */
    public static PointChargeOrderResponse from(PointChargeOrder o) {
        return PointChargeOrderResponse.builder()
                .id(o.getPtChgOrdSn())
                .date(o.getPtChgOrdRegDt() != null ? o.getPtChgOrdRegDt().format(DATE_FMT) : null)
                .orderNo(o.getPtChgOrdNo())
                .amount(o.getPtChgOrdAmt())
                .status(o.getStatusNm())
                .statusCd(o.getPtChgOrdStatusCd())
                .failReason(o.getPtChgOrdFailRsnCn())
                .build();
    }
}
