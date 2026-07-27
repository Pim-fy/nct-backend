package nct.ops.notice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.ops.notice.dto.AdminNoticeActionRequest;
import nct.ops.notice.dto.AdminNoticeDetailResponse;
import nct.ops.notice.dto.AdminNoticeOptionsResponse;
import nct.ops.notice.dto.AdminNoticePageResponse;
import nct.ops.notice.dto.AdminNoticeUpsertRequest;
import nct.ops.notice.service.AdminNoticeService;

/**
 * F-OPS-023 관리자 공지 API다.
 * `/api/admin/**`는 SecurityConfig에서 ROLE_ADMIN만 통과하며 서비스에서도 행위자 번호를 재확인한다.
 */
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<AdminNoticeOptionsResponse>> getOptions() {
        return ResponseEntity.ok(ApiResponse.success(adminNoticeService.getOptions()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminNoticePageResponse>> getNotices(
            @RequestParam(name = "typeCode", required = false) String typeCode,
            @RequestParam(name = "statusCode", required = false) String statusCode,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminNoticeService.getNotices(typeCode, statusCode, keyword, page, size)));
    }

    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> getNotice(
            @PathVariable(name = "noticeId") Long noticeId) {
        return ResponseEntity.ok(ApiResponse.success(adminNoticeService.getNotice(noticeId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> createNotice(
            @Valid @RequestBody AdminNoticeUpsertRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                adminNoticeService.createNotice(request, actorUserId(userDetails))));
    }

    @PutMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> updateNotice(
            @PathVariable(name = "noticeId") Long noticeId,
            @Valid @RequestBody AdminNoticeUpsertRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                adminNoticeService.updateNotice(noticeId, request, actorUserId(userDetails))));
    }

    @PatchMapping("/{noticeId}/hide")
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> hideNotice(
            @PathVariable(name = "noticeId") Long noticeId,
            @Valid @RequestBody AdminNoticeActionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                adminNoticeService.hideNotice(
                        noticeId, request.getChangeReason(), actorUserId(userDetails))));
    }

    @DeleteMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(
            @PathVariable(name = "noticeId") Long noticeId,
            @Valid @RequestBody AdminNoticeActionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        adminNoticeService.deleteNotice(
                noticeId, request.getChangeReason(), actorUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success());
    }

    private Long actorUserId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
