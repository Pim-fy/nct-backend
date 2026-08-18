package nct.provider.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.service.ProviderAccessGuard;
import nct.provider.dto.ProviderProfileRequest;
import nct.provider.dto.ProviderProfileResponse;
import nct.provider.service.ProviderProfileService;

/** 담당자 7, F-PROV-004/F-PROV-016: 본인 관리와 로그인 회원의 제공자 프로필 조회 API다. */
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderProfileController {
    private final ProviderProfileService service;
    private final ProviderAccessGuard providerAccessGuard;

    @GetMapping("/me/profile")
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<ProviderProfileResponse>> mine(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(service.getMine(userId(user))));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<ProviderProfileResponse>> update(@AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ProviderProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateMine(userId(user), request)));
    }

    /**
     * 담당자 7 연결 작업: 견적 작성 화면이 제출 전에 현재 제공자의 카테고리별 권한을 확인한다.
     * 실제 견적 제출과 같은 역할·카테고리 승인·제재 규칙을 사용하되, 이 조회는 상태를 변경하지 않는다.
     */
    @GetMapping("/me/categories/{categorySn}/quote-access")
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<Boolean>> quoteAccess(
            Authentication authentication,
            @PathVariable(name = "categorySn") Long categorySn) {
        providerAccessGuard.requireServiceAccess(authentication, categorySn);
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping("/{providerUserSn}/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProviderProfileResponse>> publicProfile(
            @PathVariable(name = "providerUserSn") Long providerUserSn) {
        return ResponseEntity.ok(ApiResponse.success(service.getPublic(providerUserSn)));
    }

    private Long userId(CustomUserDetails user) {
        if (user == null || user.getMember() == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return user.getMember().getId();
    }
}
