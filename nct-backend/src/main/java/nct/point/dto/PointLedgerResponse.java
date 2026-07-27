package nct.point.dto;

import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;
import nct.point.domain.PointLedger;

/**
 * [포인트 - 원장 행 응답 DTO]
 * - GET /api/point/ledger 응답 본문의 배열 원소
 * - 필드명·날짜 형식은 프론트 더미(PointLedgerTable)와 동일하게 맞춰
 *   프론트 컴포넌트 수정 없이 그대로 렌더링되도록 한다
 */
@Getter
@Builder
public class PointLedgerResponse {

    /** 화면 표시용 날짜 형식 (더미 데이터와 동일: "2026-07-12 14:00") */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final String date;

    /** 원장유형 한글명 (충전/홀딩/반환/보관금전환/정산/보정) — 프론트 배지 색 분기 키 */
    private final String type;
    private final String typeCd;

    /** 포인트분류 한글명 (사용가능/홀딩/정산가능) */
    private final String category;
    private final String categoryCd;

    /** 변동 금액 (증가 +, 감소 −) */
    private final long amount;
    /** 처리 후 해당 버킷 잔액 스냅샷 */
    private final long balanceAfter;

    /** 관련 건 표시 문자열 (예: "입찰-1024"), 참조 없으면 null → 화면에 '-' */
    private final String ref;
    private final String refTypeCd;
    private final Long refSn;

    private final String reason;

    /** 도메인 모델 → 응답 DTO 변환 */
    public static PointLedgerResponse from(PointLedger l) {
        return PointLedgerResponse.builder()
                .id(l.getPtLdgSn())
                .date(l.getPtLdgRegDt() != null ? l.getPtLdgRegDt().format(DATE_FMT) : null)
                .type(l.getTypeNm())
                .typeCd(l.getPtLdgTypeCd())
                .category(l.getPtTypeNm())
                .categoryCd(l.getPtLdgPtTypeCd())
                .amount(l.getPtLdgAmt())
                .balanceAfter(l.getPtLdgBalAfterAmt())
                // 참조유형명과 참조번호가 모두 있어야 "입찰-1024" 형태로 합성
                .ref(l.getRefTypeNm() != null && l.getPtLdgRefSn() != null
                        ? l.getRefTypeNm() + "-" + l.getPtLdgRefSn() : null)
                .refTypeCd(l.getPtLdgRefTypeCd())
                .refSn(l.getPtLdgRefSn())
                .reason(l.getPtLdgRsnCn())
                .build();
    }
}
