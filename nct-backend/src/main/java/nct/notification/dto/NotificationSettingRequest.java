package nct.notification.dto;

import lombok.Getter;
import lombok.Setter;
import nct.notification.domain.UserNotificationSetting;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [알림 설정 - 저장 요청 DTO] (F-COM-012)
 * - PUT /api/notification/settings 요청 본문
 * - boolean 6개 — JSON에서 누락된 필드는 false(수신 안 함)로 역직렬화된다.
 *   화면이 항상 6개 값을 모두 보내는 계약이므로 부분 갱신은 지원하지 않는다 (전체 덮어쓰기)
 */
@Getter
@Setter
public class NotificationSettingRequest {

    private boolean aucInapp;
    private boolean aucEmail;
    private boolean trdInapp;
    private boolean trdEmail;
    private boolean svcInapp;
    private boolean svcEmail;

    /** 요청 DTO(boolean) → 도메인 모델('Y'/'N') 변환 */
    public UserNotificationSetting toDomain(long usrSn) {
        UserNotificationSetting s = new UserNotificationSetting();
        s.setUsrSn(usrSn);
        s.setUsrNtfStgAucInappYn(aucInapp ? "Y" : "N");
        s.setUsrNtfStgAucEmailYn(aucEmail ? "Y" : "N");
        s.setUsrNtfStgTrdInappYn(trdInapp ? "Y" : "N");
        s.setUsrNtfStgTrdEmailYn(trdEmail ? "Y" : "N");
        s.setUsrNtfStgSvcInappYn(svcInapp ? "Y" : "N");
        s.setUsrNtfStgSvcEmailYn(svcEmail ? "Y" : "N");
        return s;
    }
}
