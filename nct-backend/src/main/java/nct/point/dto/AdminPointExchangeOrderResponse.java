package nct.point.dto;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.point.domain.PointExchangeOrder;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 환전 - 관리자 목록 행 응답 DTO] (F-PAY-012)
 * - 처리 전후 이력 목록에는 마스킹 계좌만 반환한다.
 * - 신청 상태의 계좌 원문은 감사형 단건 조회 API로만 제공한다.
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
    private final AdminMemberIdentityResponse applicantMember;

    private final long amount;

    /** 신청 시점 계좌 스냅샷 — 관리자는 반드시 이 계좌로 이체한다 (현재 등록 계좌가 아님) */
    private final String bankName;
    private final String accountNo;

    private final String statusCode;
    private final String status;
    private final Long processedBy;
    private final AdminMemberIdentityResponse processorMember;
    private final String processedDate;
    private final String rejectReason;

    /** 담당자 7 · F-PAY-012: 도메인 모델을 계좌번호가 마스킹된 관리자 목록 응답으로 변환합니다. */
    public static AdminPointExchangeOrderResponse from(PointExchangeOrder o) {
        return from(o, Map.of());
    }

    /** 담당자 7 연계 · F-PAY-012: 신청자와 처리자의 안전한 회원 식별 정보를 함께 조립합니다. */
    public static AdminPointExchangeOrderResponse from(
            PointExchangeOrder o,
            Map<Long, AdminMemberIdentityResponse> identities) {
        return AdminPointExchangeOrderResponse.builder()
                .id(o.getPtExcOrdSn())
                .date(o.getPtExcOrdRegDt() != null ? o.getPtExcOrdRegDt().format(DATE_FMT) : null)
                .userName(o.getUsrNm())
                .userSn(o.getUsrSn())
                .applicantMember(identities.get(o.getUsrSn()))
                .amount(o.getPtExcOrdAmt())
                .bankName(o.getPtExcOrdBankNm())
                .accountNo(maskAccount(o.getPtExcOrdAcntNo()))
                .statusCode(o.getPtExcOrdStatusCd())
                .status(o.getStatusNm())
                .processedBy(o.getPtExcOrdProcUsrSn())
                .processorMember(identities.get(o.getPtExcOrdProcUsrSn()))
                .processedDate(o.getPtExcOrdProcDt() != null
                        ? o.getPtExcOrdProcDt().format(DATE_FMT)
                        : null)
                .rejectReason(o.getPtExcOrdRjctRsnCn())
                .build();
    }

    private static String maskAccount(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return null;
        }
        String normalized = accountNo.replaceAll("\\s", "");
        int visibleLength = normalized.length() > 4 ? 4 : 0;
        String visible = normalized.substring(normalized.length() - visibleLength);
        return "*".repeat(Math.max(normalized.length() - visibleLength, 4)) + visible;
    }
}
