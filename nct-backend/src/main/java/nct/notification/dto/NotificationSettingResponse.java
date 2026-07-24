package nct.notification.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import nct.notification.domain.NotificationEvent;
import nct.notification.domain.UserNotificationEventSetting;

/**
 * Claude Code 작성 (BJN, 2026-07-16, 이벤트 단위로 재작성 2026-07-24)
 *
 * [알림 설정 - 응답 DTO] (F-COM-012 세분화, 목업 34_notification_settings.html)
 * - GET /api/notification/settings 응답 본문 — 이벤트 13개를 도메인·라벨과 함께 내려준다
 *   (프론트가 라벨을 다시 하드코딩하지 않도록 domain/label도 함께 전달)
 */
@Getter
@Builder
public class NotificationSettingResponse {

    private final List<Item> events;

    @Getter
    @Builder
    public static class Item {
        private final String eventCode;
        private final String domain; // NotificationDomain.name() — AUCTION/TRADE/SERVICE/OPS
        private final String label;
        private final boolean inapp;
        private final boolean email;
    }

    public static NotificationSettingResponse from(List<UserNotificationEventSetting> settings) {
        List<Item> items = settings.stream()
                .map(s -> {
                    NotificationEvent event = fromCode(s.getNtfEvtCd());
                    return Item.builder()
                            .eventCode(s.getNtfEvtCd())
                            .domain(event.getDomain().name())
                            .label(event.getLabel())
                            .inapp("Y".equals(s.getUsrNtfEvtStgInappYn()))
                            .email("Y".equals(s.getUsrNtfEvtStgEmailYn()))
                            .build();
                })
                .toList();
        return NotificationSettingResponse.builder().events(items).build();
    }

    /** NTF_EVT_CD(예: NTFC0017) → enum 역조회 — enum이 code를 필드로만 갖고 있어서 순회 조회 */
    private static NotificationEvent fromCode(String code) {
        for (NotificationEvent event : NotificationEvent.values()) {
            if (event.getCode().equals(code)) {
                return event;
            }
        }
        throw new IllegalStateException("알 수 없는 알림 이벤트 코드: " + code);
    }
}
