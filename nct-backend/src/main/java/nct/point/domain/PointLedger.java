package nct.point.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * [포인트 - 원장 행 모델]
 * - POINT_LEDGER 한 행 + 목록 조회 시 CMM_CODE 조인으로 얻는 한글명(…Nm) 필드
 * - 원장은 INSERT만 하고 절대 UPDATE/DELETE 하지 않는다 (기록 불변 — 분쟁 추적의 유일한 근거)
 */
@Data
public class PointLedger {

    private Long ptLdgSn;
    private Long usrSn;
    /** 이 변동을 일으킨 행위자 (본인 조작이면 본인, 관리자 보정이면 관리자) */
    private Long actorUsrSn;

    /** 원장참조유형공통코드(REFG01) — 어떤 건 때문에 발생했는지 (입찰/거래 등) */
    private String ptLdgRefTypeCd;
    /** 원장참조유형명 (예: 입찰, 거래) — CMM_CODE 조인 결과 */
    private String refTypeNm;
    private Long ptLdgRefSn;

    /** 포인트분류공통코드(PTLG01: 사용가능/홀딩/정산가능) — 어느 버킷이 움직였는지 */
    private String ptLdgPtTypeCd;
    private String ptTypeNm;

    /** 원장유형공통코드(PTLG02: 충전/홀딩/반환/보관금전환/정산/보정) — 왜 움직였는지 */
    private String ptLdgTypeCd;
    private String typeNm;

    /** 변동 금액 (증가 +, 감소 −) */
    private long ptLdgAmt;
    /** 처리 후 해당 버킷 잔액 스냅샷 — 표시·감사용이며 실제 잔액 계산은 항상 원장 SUM */
    private long ptLdgBalAfterAmt;
    private String ptLdgRsnCn;

    private LocalDateTime ptLdgRegDt;
}
