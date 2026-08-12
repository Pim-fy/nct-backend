package nct.provider.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import nct.provider.dto.ProviderApplicationResponse;
import nct.provider.dto.ProviderDecisionRequest;
import nct.provider.dto.ProviderPermissionChangeRequest;
import nct.provider.service.ProviderApplicationService;
import nct.provider.service.ProviderPermissionAdminService;

/**
 * 담당자 7 · F-PROV-002/003/007: 관리자 심사 API입니다.
 * /api/admin 아래 Security의 ROLE_ADMIN 검증을 사용합니다.
 */
@RestController
@RequestMapping("/api/admin/provider-applications")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminProviderApplicationController {
    private final ProviderApplicationService service;
    private final ProviderPermissionAdminService permissionAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProviderApplicationResponse>>> list(
            @RequestParam(name = "statusCode", required = false) String statusCode) {
        return ResponseEntity.ok(ApiResponse.success(service.getForAdmin(statusCode)));
    }

    @PostMapping("/{applicationSn}/approve")
    public ResponseEntity<ApiResponse<ProviderApplicationResponse>> approve(
            @PathVariable(name = "applicationSn") Long applicationSn,
            @Valid @RequestBody ProviderDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                service.approve(applicationSn, request.getReason(), userId(user))));
    }

    @PostMapping("/{applicationSn}/reject")
    public ResponseEntity<ApiResponse<ProviderApplicationResponse>> reject(
            @PathVariable(name = "applicationSn") Long applicationSn,
            @Valid @RequestBody ProviderDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(
                ApiResponse.success(service.reject(applicationSn, request.getReason(), userId(user))));
    }

    /** 담당자 7 · F-PROV-007: 승인 이력을 지우지 않고 해당 카테고리 권한만 정지합니다. */
    @PostMapping("/{applicationSn}/permission/stop")
    public ResponseEntity<ApiResponse<ProviderApplicationResponse>> stopPermission(
            @PathVariable(name = "applicationSn") Long applicationSn,
            @Valid @RequestBody ProviderPermissionChangeRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(permissionAdminService.changePermission(
                applicationSn, false, request.reason(), request.requestId(), userId(user))));
    }

    /** 담당자 7 · F-PROV-007: 정지했던 카테고리 권한을 같은 신청 이력에 연결해 복구합니다. */
    @PostMapping("/{applicationSn}/permission/restore")
    public ResponseEntity<ApiResponse<ProviderApplicationResponse>> restorePermission(
            @PathVariable(name = "applicationSn") Long applicationSn,
            @Valid @RequestBody ProviderPermissionChangeRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(permissionAdminService.changePermission(
                applicationSn, true, request.reason(), request.requestId(), userId(user))));
    }

    private Long userId(CustomUserDetails user) {
        if (user == null || user.getMember() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return user.getMember().getId();
    }
}
