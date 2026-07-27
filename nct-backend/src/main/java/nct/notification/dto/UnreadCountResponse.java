package nct.notification.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * [알림 - 미읽음 개수 응답 DTO]
 * - GET /api/notification/unread-count 응답 본문 (헤더 종 배지 확장 대비)
 */
@Getter
@Builder
public class UnreadCountResponse {

    private final int count;
}
