package nct.notification.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-24)
 *
 * [알림 - 사용자 알림 이벤트별 설정 모델] (F-COM-012 세분화)
 * - USER_NOTIFICATION_EVENT_SETTING 한 행 = 회원 1명 × 이벤트 1개(NTF_EVT_CD, NTFG05).
 *   UNIQUE(USR_SN, NTF_EVT_CD) — 회원당 이벤트마다 최대 1행
 * - 행이 없는 (회원, 이벤트) 조합은 "수신(Y)"으로 간주한다(저장한 적 없으면 기본 전부 수신).
 * - 기존 도메인 단위 USER_NOTIFICATION_SETTING(경매/거래/서비스 3종 고정컬럼)은 그대로 유지하고
 *   이 테이블은 별도로 추가한 것 — 신규 테이블 DDL은 사용자가 직접 적용(라이브 DB 직접 접속 금지 원칙)
 */
@Data
public class UserNotificationEventSetting {

    private Long usrNtfEvtStgSn;
    private Long usrSn;
    private String ntfEvtCd;

    private String usrNtfEvtStgInappYn;
    private String usrNtfEvtStgEmailYn;

    private LocalDateTime usrNtfEvtStgRegDt;
    private LocalDateTime usrNtfEvtStgUpdtDt;

    /** 저장한 적 없는 (회원, 이벤트) 조합의 기본값 — 전 채널 수신(Y) */
    public static UserNotificationEventSetting defaultOf(long usrSn, String ntfEvtCd) {
        UserNotificationEventSetting s = new UserNotificationEventSetting();
        s.setUsrSn(usrSn);
        s.setNtfEvtCd(ntfEvtCd);
        s.setUsrNtfEvtStgInappYn("Y");
        s.setUsrNtfEvtStgEmailYn("Y");
        return s;
    }
}
