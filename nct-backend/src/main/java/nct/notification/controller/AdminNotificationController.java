// Claude Code 작성 (BJN, 2026-07-19)
package nct.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.notification.dto.AdminNotificationSummaryResponse;
import nct.notification.service.AdminNotificationService;

/**
 * [알림 - 관리자 알림함 REST 컨트롤러] (담당자6, F-COM-004/005, 03_관리자/20_notification.html)
 *
 * GET /api/admin/notifications/summary — /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN만 통과
 */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminNotificationSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminNotificationService.getSummary()));
    }
}
