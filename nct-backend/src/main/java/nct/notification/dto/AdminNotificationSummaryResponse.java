// Claude Code 작성 (BJN, 2026-07-19)
package nct.notification.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * [알림 - 관리자 알림함 요약 응답] (담당자6, F-COM-004/005, 03_관리자/20_notification.html)
 * - GET /api/admin/notifications/summary 응답 본문
 * - 목업의 4개 탭(회원·제공자 / 신고·거래문제 / 경매·서비스 / 환전·시스템)과 1:1 대응
 */
@Getter
@Builder
public class AdminNotificationSummaryResponse {

    private List<AdminNotificationItem> userProvider;
    private List<AdminNotificationItem> report;
    private List<AdminNotificationItem> auctionService;
    private List<AdminNotificationItem> exchangeSystem;
}
