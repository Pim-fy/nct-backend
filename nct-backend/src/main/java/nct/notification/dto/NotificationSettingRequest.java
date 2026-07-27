package nct.notification.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import nct.notification.domain.UserNotificationEventSetting;

/**
 * Claude Code 작성 (BJN, 2026-07-16, 이벤트 단위로 재작성 2026-07-24)
 *
 * [알림 설정 - 저장 요청 DTO] (F-COM-012 세분화)
 * - PUT /api/notification/settings 요청 본문 — 화면이 13개 이벤트 항목을 전부 보내는 계약
 *   (부분 갱신은 지원하지 않는다 — 전체 덮어쓰기)
 */
@Getter
@Setter
public class NotificationSettingRequest {

    private List<Item> events;

    @Getter
    @Setter
    public static class Item {
        private String eventCode;
        private boolean inapp;
        private boolean email;
    }

    /** 요청 DTO(boolean) → 도메인 모델('Y'/'N') 리스트 변환 */
    public List<UserNotificationEventSetting> toDomain(long usrSn) {
        return events.stream()
                .map(item -> {
                    UserNotificationEventSetting s = new UserNotificationEventSetting();
                    s.setUsrSn(usrSn);
                    s.setNtfEvtCd(item.getEventCode());
                    s.setUsrNtfEvtStgInappYn(item.isInapp() ? "Y" : "N");
                    s.setUsrNtfEvtStgEmailYn(item.isEmail() ? "Y" : "N");
                    return s;
                })
                .toList();
    }
}
