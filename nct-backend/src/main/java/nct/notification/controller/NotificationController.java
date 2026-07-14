package nct.notification.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.notification.dto.NotificationResponse;
import nct.notification.dto.UnreadCountResponse;
import nct.notification.service.NotificationService;

/**
 * [알림 - REST 컨트롤러] (담당자6)
 *
 * 엔드포인트 (모두 로그인 필요):
 *   GET   /api/notification               내 알림 목록 (최신순 100건)
 *   GET   /api/notification/unread-count  미읽음 개수 (헤더 배지용)
 *   PATCH /api/notification/{id}/read     개별 읽음 처리
 *   PATCH /api/notification/read-all      전체 읽음 처리
 *
 * 설계 원칙:
 * - 사용자 식별은 항상 인증 토큰에서 — 남의 알림 조회/읽음 처리 차단
 * - 알림 생성은 HTTP로 노출하지 않는다 — 각 도메인 서비스가 notify(...)를 서버 내부에서 호출
 */
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 내 알림 목록 조회 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        List<NotificationResponse> body = notificationService.getList(usrSn).stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 미읽음 개수 조회 */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        UnreadCountResponse body = UnreadCountResponse.builder()
                .count(notificationService.getUnreadCount(usrSn))
                .build();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 개별 읽음 처리 — id가 남의 알림이면 usrSn 가드로 아무 일도 일어나지 않음 */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        notificationService.markRead(id, userDetails.getMember().getId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 전체 읽음 처리 */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        notificationService.markAllRead(userDetails.getMember().getId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
