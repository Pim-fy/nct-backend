package nct.notification.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * [알림 - 알림 행 모델]
 * - NOTIFICATION 한 행 + 목록 조회 시 CMM_CODE 조인으로 얻는 한글명(…Nm) 필드
 */
@Data
public class Notification {

    private Long ntfSn;
    private Long usrSn;

    /** 알림유형공통코드(NTFG01) */
    private String ntfTypeCd;
    /** 알림유형 한글명 (예: 입찰, 거래) — CMM_CODE 조인 결과 */
    private String typeNm;

    /** 알림도메인공통코드(NTFG03) — 알림함 필터 기준 */
    private String ntfDomainCd;
    /** 알림도메인 한글명 — CMM_CODE 조인 결과 */
    private String domainNm;

    /** 알림대상구분공통코드(NTFG04) — 일반/제공자 필터 기준 (F-COM-011) */
    private String ntfAudienceCd;

    private String ntfTtl;
    private String ntfCn;

    /** 참조유형공통코드(REFG01) — 어떤 건에서 발생한 알림인지 (없을 수 있음) */
    private String ntfRefTypeCd;
    private Long ntfRefSn;

    /** 읽음여부 Y/N */
    private String ntfReadYn;
    private LocalDateTime ntfReadDt;

    /** 이메일발송상태공통코드(NTFG02) — 이메일 채널 미확정(DEC-064)이라 현재는 전부 미대상 */
    private String ntfEmailStatusCd;

    private LocalDateTime ntfRegDt;
}
