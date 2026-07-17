package nct.point.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 - 환전 주문 모델] (F-PAY-012, D-026)
 * - POINT_EXCHANGE_ORDER 한 행. 환전 신청 한 건의 전 과정(신청→완료/반려)을 추적한다
 * - 신청 즉시 정산가능 포인트를 차감(환전출금 원장)하고 그 원장 SN을 여기 연결한다 —
 *   이중 신청을 원천 차단하고, 반려 시엔 복원 원장을 짝으로 연결한다
 * - 은행명·계좌번호는 신청 시점 스냅샷 — 신청 후 회원이 계좌를 바꿔도
 *   "신청 당시 어디로 보내달라 했는지"가 남아 관리자 이체·분쟁 대응 근거가 된다
 */
@Data
public class PointExchangeOrder {

    private Long ptExcOrdSn;
    private Long usrSn;

    /** 신청 금액 (환전 가능 = 정산가능 포인트에서 차감) */
    private long ptExcOrdAmt;

    /** 환전주문상태공통코드(PEOG01: 신청/완료/반려) */
    private String ptExcOrdStatusCd;

    /** 차감 포인트원장일련번호 — 신청 즉시 기록 (필수) */
    private Long ptExcOrdDeductLdgSn;
    /** 복원 포인트원장일련번호 — 반려 시 기록 */
    private Long ptExcOrdRestoreLdgSn;

    /** 처리자(관리자) 회원일련번호 — 지급완료/반려 처리 시 기록 */
    private Long ptExcOrdProcUsrSn;
    private LocalDateTime ptExcOrdProcDt;
    private String ptExcOrdRjctRsnCn;

    /** 신청 시점 계좌 스냅샷 */
    private String ptExcOrdBankNm;
    private String ptExcOrdAcntNo;

    private LocalDateTime ptExcOrdRegDt;
    private LocalDateTime ptExcOrdUpdtDt;

    /** 상태 한글명 (목록 조회 시 CMM_CODE 조인으로 채움 — 충전 주문의 statusNm과 같은 방식) */
    private String statusNm;

    /** 신청자 이름 (관리자 처리 대기 목록에서만 USERS 조인으로 채움) */
    private String usrNm;
}
