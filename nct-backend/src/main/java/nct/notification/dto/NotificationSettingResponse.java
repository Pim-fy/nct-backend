package nct.notification.dto;

import lombok.Builder;
import lombok.Getter;
import nct.notification.domain.UserNotificationSetting;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [알림 설정 - 응답 DTO] (F-COM-012)
 * - GET /api/notification/settings 응답 본문
 * - DB의 'Y'/'N' 문자를 프론트가 체크박스에 바로 쓰도록 boolean으로 변환해 내려준다
 */
@Getter
@Builder
public class NotificationSettingResponse {

    /** 경매 도메인 인앱/이메일 수신 여부 */
    private final boolean aucInapp;
    private final boolean aucEmail;

    /** 거래 도메인 인앱/이메일 수신 여부 */
    private final boolean trdInapp;
    private final boolean trdEmail;

    /** 서비스 도메인 인앱/이메일 수신 여부 */
    private final boolean svcInapp;
    private final boolean svcEmail;

    /** 도메인 모델('Y'/'N') → 응답 DTO(boolean) 변환 */
    public static NotificationSettingResponse from(UserNotificationSetting s) {
        return NotificationSettingResponse.builder()
                .aucInapp("Y".equals(s.getUsrNtfStgAucInappYn()))
                .aucEmail("Y".equals(s.getUsrNtfStgAucEmailYn()))
                .trdInapp("Y".equals(s.getUsrNtfStgTrdInappYn()))
                .trdEmail("Y".equals(s.getUsrNtfStgTrdEmailYn()))
                .svcInapp("Y".equals(s.getUsrNtfStgSvcInappYn()))
                .svcEmail("Y".equals(s.getUsrNtfStgSvcEmailYn()))
                .build();
    }
}
