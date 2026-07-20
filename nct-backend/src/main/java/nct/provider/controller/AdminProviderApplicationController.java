package nct.provider.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
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
import nct.provider.service.ProviderApplicationService;

/** 담당자 7 · F-PROV-002/003/007: 관리자 심사 API입니다. /api/admin 아래 Security의 ROLE_ADMIN 검증을 사용합니다. */
@RestController @RequestMapping("/api/admin/provider-applications") @RequiredArgsConstructor
public class AdminProviderApplicationController {
    private final ProviderApplicationService service;
    @GetMapping public ResponseEntity<ApiResponse<List<ProviderApplicationResponse>>> list(@RequestParam(name = "statusCode", required = false) String statusCode) { return ResponseEntity.ok(ApiResponse.success(service.getForAdmin(statusCode))); }
    @PostMapping("/{applicationSn}/approve") public ResponseEntity<ApiResponse<ProviderApplicationResponse>> approve(@PathVariable(name = "applicationSn") Long applicationSn, @AuthenticationPrincipal CustomUserDetails user) { return ResponseEntity.ok(ApiResponse.success(service.approve(applicationSn, userId(user)))); }
    @PostMapping("/{applicationSn}/reject") public ResponseEntity<ApiResponse<ProviderApplicationResponse>> reject(@PathVariable(name = "applicationSn") Long applicationSn, @Valid @RequestBody ProviderDecisionRequest request, @AuthenticationPrincipal CustomUserDetails user) { return ResponseEntity.ok(ApiResponse.success(service.reject(applicationSn, request.getReason(), userId(user)))); }
    private Long userId(CustomUserDetails user) { if (user == null || user.getMember() == null) throw new CustomException(ErrorCode.UNAUTHORIZED); return user.getMember().getId(); }
}
