package nct.ops.notice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.notice.dto.PublicNoticeDetailResponse;
import nct.ops.notice.dto.PublicNoticePageResponse;
import nct.ops.notice.dto.PublicNoticeTypeResponse;
import nct.ops.notice.service.PublicNoticeService;

/**
 * 방문자와 로그인 사용자가 함께 쓰는 공지 읽기 API다.
 * 쓰기 기능은 이 경로에 만들지 않고 추후 ROLE_ADMIN 전용 `/api/admin/notices`로 분리한다.
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class PublicNoticeController {

    private final PublicNoticeService publicNoticeService;

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<PublicNoticeTypeResponse>>> getPublicNoticeTypes() {
        return ResponseEntity.ok(ApiResponse.success(publicNoticeService.getPublicNoticeTypes()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PublicNoticePageResponse>> getPublicNotices(
            @RequestParam(name = "typeCode", required = false) String typeCode,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                publicNoticeService.getPublicNotices(typeCode, page, size)));
    }

    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<PublicNoticeDetailResponse>> getPublicNotice(
            @PathVariable(name = "noticeId") Long noticeId) {
        return ResponseEntity.ok(ApiResponse.success(
                publicNoticeService.getPublicNotice(noticeId)));
    }
}
