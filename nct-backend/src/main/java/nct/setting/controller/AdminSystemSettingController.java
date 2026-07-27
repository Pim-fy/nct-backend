package nct.setting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.setting.domain.SystemSettingDetail;
import nct.setting.service.SystemSettingAdminService;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [시스템 설정 - 관리자 REST 컨트롤러] (F-OPS-024)
 *
 * 엔드포인트 (전부 관리자 전용 — /api/admin/**는 SecurityConfig에서 ROLE_ADMIN만 통과):
 *   GET /api/admin/system-setting   전체 설정 조회
 *   PUT /api/admin/system-setting   전체 설정 수정 (범위 검증 + 감사로그 자동 기록)
 *
 * 값 검증은 서비스에서 한다 — 컨트롤러 bean validation 대신 서비스 검증인 이유:
 * "허용 범위"가 검증 핵심이라 필드 간 관계(최소≤최대, 점검 기간 순서)까지 한 곳에서 본다.
 */
@RestController
@RequestMapping("/api/admin/system-setting")
@RequiredArgsConstructor
public class AdminSystemSettingController {

    private final SystemSettingAdminService settingService;

    /** 전체 설정 조회 — 관리자 설정 화면 초기값 */
    @GetMapping
    public ResponseEntity<ApiResponse<SystemSettingDetail>> get() {
        return ResponseEntity.ok(ApiResponse.success(settingService.get()));
    }

    /** 전체 설정 수정 — 저장된 최신값을 그대로 돌려줘 화면이 재조회 없이 갱신되게 한다 */
    @PutMapping
    public ResponseEntity<ApiResponse<SystemSettingDetail>> update(
            @RequestBody SystemSettingDetail request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {

        long adminUsrSn = userDetails.getMember().getId();
        SystemSettingDetail saved = settingService.update(request, adminUsrSn, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(saved));
    }
}
