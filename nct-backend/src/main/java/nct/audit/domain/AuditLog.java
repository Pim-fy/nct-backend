package nct.audit.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [감사 - 감사로그 행 모델] (F-OPS-015)
 * - AUDIT_LOG 한 행. 포인트·정산·관리자 조치·민감정보 접근을 추적하는 기록 단위 (3년 보존 대상)
 * - 뒤쪽 *Nm 필드들은 테이블 컬럼이 아니라 조회 시 CMM_CODE·USERS를 조인해 채우는
 *   화면 표시용 값이다 (원장 목록 조회와 같은 방식)
 */
@Data
public class AuditLog {

    private Long audLogSn;
    /** 행위자 회원일련번호 — 시스템 자동 기록이면 null */
    private Long usrSn;
    /** 감사로그유형공통코드(AUDG01) */
    private String audLogTypeCd;
    /** 참조유형공통코드(REFG01) — 없는 행위면 null */
    private String audLogRefTypeCd;
    private Long audLogRefSn;
    /** 사유내용 — 민감정보 제한 조회는 필수 */
    private String audLogRsonCn;
    private String audLogIpAddr;
    private LocalDateTime audLogRegDt;

    // ---------- 아래는 조회 전용(조인) 표시 필드 ----------

    /** 행위자 이름 (USERS 조인) */
    private String usrNm;
    /** 감사 행위 유형 한글명 (CMM_CODE 조인) */
    private String audLogTypeNm;
    /** 참조유형 한글명 (CMM_CODE 조인) */
    private String refTypeNm;
}
