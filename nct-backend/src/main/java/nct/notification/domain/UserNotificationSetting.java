package nct.notification.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [알림 - 사용자 알림 환경설정 모델] (F-COM-012)
 * - USER_NOTIFICATION_SETTING 한 행. 회원당 1행(UK_USER_NOTIFICATION_SETTING_USR)
 * - 도메인 3종(경매/거래/서비스) × 채널 2종(인앱/이메일) 고정 컬럼 구조 — 정본 DDL v4 기준
 *   (기능정의서에는 '운영' 도메인도 언급되나 DDL에 컬럼이 없어 제외 — 정본 불일치 보고 대상)
 * - 행이 없는 회원은 "전부 수신(Y)"으로 간주한다 — DDL DEFAULT 'Y'와 같은 의미
 */
@Data
public class UserNotificationSetting {

    private Long usrNtfStgSn;
    private Long usrSn;

    /** 경매 인앱/이메일 수신여부 ('Y'/'N') */
    private String usrNtfStgAucInappYn;
    private String usrNtfStgAucEmailYn;

    /** 거래 인앱/이메일 수신여부 */
    private String usrNtfStgTrdInappYn;
    private String usrNtfStgTrdEmailYn;

    /** 서비스 인앱/이메일 수신여부 */
    private String usrNtfStgSvcInappYn;
    private String usrNtfStgSvcEmailYn;

    private LocalDateTime usrNtfStgRegDt;
    private LocalDateTime usrNtfStgUpdtDt;

    /** 행이 없는 회원용 기본값 — 전 채널 수신(Y) */
    public static UserNotificationSetting defaultOf(long usrSn) {
        UserNotificationSetting s = new UserNotificationSetting();
        s.setUsrSn(usrSn);
        s.setUsrNtfStgAucInappYn("Y");
        s.setUsrNtfStgAucEmailYn("Y");
        s.setUsrNtfStgTrdInappYn("Y");
        s.setUsrNtfStgTrdEmailYn("Y");
        s.setUsrNtfStgSvcInappYn("Y");
        s.setUsrNtfStgSvcEmailYn("Y");
        return s;
    }
}
