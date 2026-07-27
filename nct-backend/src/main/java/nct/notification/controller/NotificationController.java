package nct.notification.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.notification.dto.NotificationResponse;
import nct.notification.dto.NotificationSettingRequest;
import nct.notification.dto.NotificationSettingResponse;
import nct.notification.dto.UnreadCountResponse;
import nct.notification.service.NotificationEventBroker;
import nct.notification.service.NotificationService;
import reactor.core.publisher.Flux;

/**
 * [알림 - REST 컨트롤러] (담당자6)
 *
 * 엔드포인트 (모두 로그인 필요):
 *   GET   /api/notification               내 알림 목록 (최신순 100건)
 *   GET   /api/notification/unread-count  미읽음 개수 (헤더 배지용)
 *   GET   /api/notification/stream        실시간 push 구독 (SSE, F-COM-XXX)
 *   PATCH /api/notification/{id}/read     개별 읽음 처리
 *   PATCH /api/notification/read-all      전체 읽음 처리
 *   GET   /api/notification/settings      내 알림 수신 설정 조회 — F-COM-012
 *   PUT   /api/notification/settings      내 알림 수신 설정 저장 (전체 덮어쓰기) — F-COM-012
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
    private final NotificationEventBroker eventBroker;

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

    /**
     * 실시간 알림 스트림 구독 (SSE). 새로고침 없이 알림·배지가 즉시 갱신되도록 클라이언트가
     * 로그인 상태 동안 계속 열어두는 연결이다 — X-Accel-Buffering 헤더는 리버스 프록시가
     * 스트림을 버퍼링하지 않도록 하는 방어용.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<NotificationResponse>>> stream(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .body(eventBroker.subscribe(usrSn));
    }

    /** 개별 읽음 처리 — id가 남의 알림이면 usrSn 가드로 아무 일도 일어나지 않음 */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable(name = "id") long id,
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

    /**
     * 내 알림 수신 설정 조회 — 이벤트 13개 전부(F-COM-012 세분화, 2026-07-24).
     * 저장한 적 없는 이벤트는 기본값(전 채널 수신)이 내려간다
     */
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> getSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        NotificationSettingResponse body =
                NotificationSettingResponse.from(notificationService.getEventSettings(usrSn));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 내 알림 수신 설정 저장 — 화면이 이벤트 13개 값을 전부 보내는 전체 덮어쓰기 계약 */
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<Void>> saveSettings(
            @RequestBody NotificationSettingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long usrSn = userDetails.getMember().getId();
        notificationService.saveEventSettings(usrSn, request.toDomain(usrSn));
        return ResponseEntity.ok(ApiResponse.success());
    }
}
